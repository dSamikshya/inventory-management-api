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
    private Long purchaseOrderId;
    private Integer quantity;
    private Integer remainingQuantity;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private LocalDateTime expiryDate;
    private String notes;
    private Boolean isExpired;
    private Boolean isAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
