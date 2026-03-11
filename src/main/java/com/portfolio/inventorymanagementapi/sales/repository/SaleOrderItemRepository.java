package com.portfolio.inventorymanagementapi.sales.repository;

import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleOrderItemRepository extends JpaRepository<SaleOrderItem, Long> {

    List<SaleOrderItem> findBySaleOrderId(Long saleOrderId);

    List<SaleOrderItem> findByProductId(Long productId);

    @Query("SELECT si FROM SaleOrderItem si WHERE si.product.id = :productId " +
            "AND si.saleOrder.status = 'DELIVERED'")
    List<SaleOrderItem> findDeliveredItemsByProduct(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(si.quantityFulfilled), 0) FROM SaleOrderItem si " +
            "WHERE si.product.id = :productId AND si.saleOrder.status = 'DELIVERED'")
    Integer getTotalSoldQuantityByProduct(@Param("productId") Long productId);
}