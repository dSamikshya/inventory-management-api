package com.portfolio.inventorymanagementapi.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private String categoryName;
    private Long supplierId;
    private String supplierName;
    private String purchaseOrderId;
    private Integer quantity;
    private Integer remainingQuantity;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal remainingValue;
    private LocalDateTime expiryDate;
    private String notes;
    private Boolean isExpired;
    private Boolean isAvailable;
    private Boolean isDepleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}