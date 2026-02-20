package com.portfolio.inventorymanagementapi.product.entity;
import com.portfolio.inventorymanagementapi.supplier.entity.Supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ProductBatch - Represents one shipment/lot of a product
 *
 * BUSINESS RULES:
 * 1. Multiple batches can exist for same SKU (different shipments)
 * 2. Each batch has its own cost price
 * 3. FIFO deduction when selling
 * 4. remainingQuantity can NEVER be negative
 */
@Entity
@Table(name = "product_batches", indexes = {
        @Index(name = "idx_product_batch", columnList = "product_id"),
        @Index(name = "idx_purchase_order", columnList = "purchase_order_id"),
        @Index(name = "idx_supplier", columnList = "supplier_id"),
        @Index(name = "idx_created_at", columnList = "created_at")  // For FIFO
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which product this batch belongs to (by SKU)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Which supplier provided this batch
     * Important for tracking source and quality
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /**
     * Purchase Order number
     * Multiple batches can have same PO (one shipment, multiple products)
     */
    @Column(name = "purchase_order_id", length = 100)
    private String purchaseOrderId;

    /**
     * Original quantity received
     * NEVER changes after creation
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Current remaining quantity
     * Decreases when products are sold
     * NEVER goes negative (validation required!)
     */
    @Column(nullable = false)
    private Integer remainingQuantity;

    /**
     * Cost price for THIS batch
     * Different batches of same product can have different costs!
     * This is why batch tracking is crucial for profit calculation
     */
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal costPrice;

    /**
     * Selling price (can override product's default)
     */
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    /**
     * When this batch expires (if applicable)
     * Used for FEFO (First Expired, First Out) if needed
     */
    private LocalDateTime expiryDate;

    @Column(length = 500)
    private String notes;

    /**
     * When batch was received
     * CRITICAL for FIFO (First In, First Out)
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ==================== HELPER METHODS ====================

    /**
     * Check if batch still has stock
     */
    public boolean isAvailable() {
        return remainingQuantity > 0;
    }

    /**
     * Check if batch is expired
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
    }

    /**
     * Check if batch is depleted (fully sold)
     */
    public boolean isDepleted() {
        return remainingQuantity == 0;
    }

    /**
     * Deduct quantity from batch (for sales)
     * CRITICAL: Validates against negative quantity!
     */
    public void deductQuantity(Integer quantityToDeduct) {
        if (quantityToDeduct == null || quantityToDeduct <= 0) {
            throw new IllegalArgumentException("Quantity to deduct must be positive");
        }

        if (quantityToDeduct > remainingQuantity) {
            throw new IllegalArgumentException(
                    String.format("Insufficient quantity in batch. Available: %d, Requested: %d",
                            remainingQuantity, quantityToDeduct)
            );
        }

        this.remainingQuantity -= quantityToDeduct;
    }

    /**
     * Add quantity to batch (for returns/adjustments)
     */
    public void addQuantity(Integer quantityToAdd) {
        if (quantityToAdd == null || quantityToAdd <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive");
        }

        this.remainingQuantity += quantityToAdd;
        this.quantity += quantityToAdd;  // Also update original quantity
    }

    /**
     * Calculate value of remaining stock in this batch
     */
    public BigDecimal getRemainingValue() {
        return costPrice.multiply(BigDecimal.valueOf(remainingQuantity));
    }

    /**
     * Calculate potential profit if all remaining stock sold
     */
    public BigDecimal getPotentialProfit() {
        BigDecimal revenue = sellingPrice.multiply(BigDecimal.valueOf(remainingQuantity));
        BigDecimal cost = costPrice.multiply(BigDecimal.valueOf(remainingQuantity));
        return revenue.subtract(cost);
    }
}