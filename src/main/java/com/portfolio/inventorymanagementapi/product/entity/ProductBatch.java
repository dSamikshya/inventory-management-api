package com.portfolio.inventorymanagementapi.product.entity;


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
@Table(name = "product_batches", indexes = {
        @Index(name = "idx_product_batch", columnList = "product_id"),
        @Index(name = "idx_purchase_order", columnList = "purchase_order_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId; // Reference to purchase order

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer remainingQuantity;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal costPrice;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    private LocalDateTime expiryDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isAvailable() {
        return remainingQuantity > 0;
    }

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
    }

    public void deductQuantity(Integer quantity) {
        if (quantity > remainingQuantity) {
            throw new IllegalArgumentException("Insufficient quantity in batch");
        }
        this.remainingQuantity -= quantity;
    }

    public void addQuantity(Integer quantity) {
        this.remainingQuantity += quantity;
        this.quantity += quantity;
    }
}