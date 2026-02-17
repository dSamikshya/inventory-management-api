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
public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String description;
    private Long categoryId;
    private String categoryName;
    private String barcode;
    private BigDecimal weight;
    private Integer totalQuantity;
    private Integer reorderThreshold;
    private BigDecimal averageCostPrice;
    private BigDecimal sellingPrice;
    private Boolean isLowStock;
    private Integer batchCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}