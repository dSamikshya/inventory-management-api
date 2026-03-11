package com.portfolio.inventorymanagementapi.sales.dto;

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
public class SaleOrderItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantityRequested;
    private Integer quantityFulfilled;
    private BigDecimal unitSellingPrice;
    private BigDecimal unitCostPrice;
    private BigDecimal totalPrice;
    private BigDecimal totalCost;
    private BigDecimal profit;
    private boolean partialFill;
    private String notes;
    private LocalDateTime createdAt;
}