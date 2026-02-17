package com.portfolio.inventorymanagementapi.customer.repository;

import com.portfolio.inventorymanagementapi.customer.entity.Customer;
import com.portfolio.inventorymanagementapi.customer.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Find by email
    Optional<Customer> findByEmail(String email);

    // Check if email exists
    boolean existsByEmail(String email);

    // Find by status
    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
    List<Customer> findByStatus(CustomerStatus status);

    // Find by phone number
    Optional<Customer> findByPhoneNumber(String phoneNumber);

    // Search by name (first or last)
    @Query("SELECT c FROM Customer c WHERE " +
            "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Search by multiple criteria
    @Query("SELECT c FROM Customer c WHERE " +
            "(:searchTerm IS NULL OR " +
            "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.phoneNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
            "(:status IS NULL OR c.status = :status)")
    Page<Customer> search(@Param("searchTerm") String searchTerm,
                          @Param("status") CustomerStatus status,
                          Pageable pageable);

    // Find customers with balance exceeding credit limit
    @Query("SELECT c FROM Customer c WHERE c.currentBalance > c.creditLimit")
    List<Customer> findCustomersExceedingCreditLimit();

    // Find customers by city
    Page<Customer> findByCity(String city, Pageable pageable);

    // Find customers by state
    Page<Customer> findByState(String state, Pageable pageable);

    // Find customers with balance greater than
    @Query("SELECT c FROM Customer c WHERE c.currentBalance > :amount")
    List<Customer> findCustomersWithBalanceGreaterThan(@Param("amount") BigDecimal amount);

    // Count by status
    long countByStatus(CustomerStatus status);
}