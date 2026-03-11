package com.portfolio.inventorymanagementapi.sales.entity;

import com.portfolio.inventorymanagementapi.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", nullable = false)
    private SaleOrder saleOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** How many units were requested */
    @Column(nullable = false)
    private Integer quantityRequested;

    /** How many units were actually fulfilled (may be less if partial fill) */
    @Column(nullable = false)
    private Integer quantityFulfilled;

    /** Selling price per unit at time of sale (snapshot — price may change later) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitSellingPrice;

    /** Weighted average cost per unit at time of sale (for profit calculation) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCostPrice;

    /** Total selling price = unitSellingPrice * quantityFulfilled */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /** Total cost = unitCostPrice * quantityFulfilled */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost;

    /** Profit = totalPrice - totalCost */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal profit;

    /** True if quantityFulfilled < quantityRequested */
    @Column(nullable = false)
    private boolean partialFill;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}