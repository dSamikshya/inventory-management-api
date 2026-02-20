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

    /** Provide one of: productId, productSku, or productName. */
    private Long productId;

    @Size(max = 100)
    private String productSku;

    @Size(max = 200)
    private String productName;

    @Size(max = 1000)
    private String productDescription;

    @Size(max = 100)
    private String categoryName;

    @Size(max = 500)
    private String categoryDescription;

    /** Optional — auto-created if not found. */
    @Size(max = 200)
    private String supplierName;

    @Size(max = 100)
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

    @Size(max = 500)
    private String notes;
}