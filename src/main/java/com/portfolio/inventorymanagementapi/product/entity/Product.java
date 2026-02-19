package com.portfolio.inventorymanagementapi.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product Entity
 *
 * Stock tracking strategy:
 *  - totalQuantity and averageCostPrice are @Formula fields — they are
 *    computed at query time directly from product_batches.
 *  - NEVER call setTotalQuantity / setAverageCostPrice from service code.
 *    They do not persist. All changes must go through ProductBatch.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_sku",      columnList = "sku",         unique = true),
        @Index(name = "idx_barcode",  columnList = "barcode"),
        @Index(name = "idx_category", columnList = "category_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SKU uniquely identifies a product variant.
     * Example: IP12-128-BLK (iPhone 12, 128GB, Black)
     */
    @Column(unique = true, nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(unique = true, length = 100)
    private String barcode;

    @Column(precision = 10, scale = 2)
    private BigDecimal weight;

    /**
     * READ-ONLY — computed from product_batches at query time.
     * Do NOT call setTotalQuantity(); it has no effect in the DB.
     */
    @Formula("(SELECT COALESCE(SUM(pb.remaining_quantity), 0) " +
            "FROM product_batches pb WHERE pb.product_id = id)")
    private Integer totalQuantity;

    /**
     * READ-ONLY — weighted-average cost computed from product_batches.
     * Do NOT call setAverageCostPrice(); it has no effect in the DB.
     */
    @Formula("(SELECT COALESCE(" +
            "  SUM(pb.cost_price * pb.remaining_quantity) / NULLIF(SUM(pb.remaining_quantity), 0)" +
            ", 0) FROM product_batches pb WHERE pb.product_id = id)")
    private BigDecimal averageCostPrice;

    @Builder.Default
    @Column(nullable = false)
    private Integer reorderThreshold = 10;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "selling_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductBatch> batches = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ==================== HELPERS ====================

    public boolean isLowStock() {
        return totalQuantity != null && totalQuantity <= reorderThreshold;
    }

    public void addBatch(ProductBatch batch) {
        batches.add(batch);
        batch.setProduct(this);
    }

    public void removeBatch(ProductBatch batch) {
        batches.remove(batch);
        batch.setProduct(null);
    }
}