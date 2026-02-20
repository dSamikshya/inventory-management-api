package com.portfolio.inventorymanagementapi.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequest {

    // ── Product identification (one of these is needed) ──────────────────────
    private Long productId;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String productSku;

    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String productName;

    @Size(max = 500, message = "Product description must not exceed 500 characters")
    private String productDescription;

    // ── Category (required when auto-creating a product) ─────────────────────
    @Size(max = 255, message = "Category name must not exceed 255 characters")
    private String categoryName;

    @Size(max = 500, message = "Category description must not exceed 500 characters")
    private String categoryDescription;

    // ── Supplier (optional — auto-created if not found) ──────────────────────
    @Size(max = 200, message = "Supplier name must not exceed 200 characters")
    private String supplierName;

    // ── Batch details ─────────────────────────────────────────────────────────
    private String purchaseOrderId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Cost price is required")
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;

    @NotNull(message = "Selling price is required")
    @PositiveOrZero(message = "Selling price must be zero or positive")
    private BigDecimal sellingPrice;

    private LocalDate expiryDate;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}