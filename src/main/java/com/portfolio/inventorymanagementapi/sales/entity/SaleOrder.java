package com.portfolio.inventorymanagementapi.sales.entity;

import com.portfolio.inventorymanagementapi.customer.entity.Customer;
import com.portfolio.inventorymanagementapi.customer.repository.CustomerRepository;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale_orders", indexes = {
        @Index(name = "idx_invoice_number", columnList = "invoiceNumber", unique = true),
        @Index(name = "idx_sale_order_status", columnList = "status"),
        @Index(name = "idx_sale_order_customer", columnList = "customer_id"),
        @Index(name = "idx_sale_order_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Auto-generated: INV-20260220-0001 */
    @Column(unique = true, nullable = false, length = 50)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleOrderStatus status = SaleOrderStatus.DRAFT;

    @OneToMany(mappedBy = "saleOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleOrderItem> items = new ArrayList<>();

    /** Sum of all item totalPrices */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Sum of all item totalCosts */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** totalAmount - totalCost */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalProfit = BigDecimal.ZERO;

    /** True if any item was partially filled */
    @Column(nullable = false)
    @Builder.Default
    private boolean partialFill = false;

    @Column(length = 1000)
    private String notes;

    /** When order was confirmed (stock deducted) */
    private LocalDateTime confirmedAt;

    /** When order was shipped */
    private LocalDateTime shippedAt;

    /** When order was delivered */
    private LocalDateTime deliveredAt;

    /** When order was cancelled */
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}