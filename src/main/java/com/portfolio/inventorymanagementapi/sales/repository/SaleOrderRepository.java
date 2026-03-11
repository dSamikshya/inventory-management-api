package com.portfolio.inventorymanagementapi.sales.repository;

import com.portfolio.inventorymanagementapi.sales.entity.SaleOrder;
import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    Optional<SaleOrder> findByInvoiceNumber(String invoiceNumber);

    Page<SaleOrder> findByStatus(SaleOrderStatus status, Pageable pageable);

    Page<SaleOrder> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT o FROM SaleOrder o WHERE o.createdAt BETWEEN :start AND :end")
    Page<SaleOrder> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query("SELECT o FROM SaleOrder o WHERE o.customer.id = :customerId AND o.status = :status")
    List<SaleOrder> findByCustomerIdAndStatus(
            @Param("customerId") Long customerId,
            @Param("status") SaleOrderStatus status);

    // For invoice number generation — get latest invoice number
    @Query("SELECT o.invoiceNumber FROM SaleOrder o WHERE o.invoiceNumber LIKE :prefix ORDER BY o.invoiceNumber DESC")
    List<String> findLatestInvoiceNumbers(@Param("prefix") String prefix);

    // Revenue stats
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM SaleOrder o WHERE o.status = 'DELIVERED'")
    java.math.BigDecimal getTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalProfit), 0) FROM SaleOrder o WHERE o.status = 'DELIVERED'")
    java.math.BigDecimal getTotalProfit();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM SaleOrder o " +
            "WHERE o.status = 'DELIVERED' AND o.deliveredAt BETWEEN :start AND :end")
    java.math.BigDecimal getRevenueInRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}