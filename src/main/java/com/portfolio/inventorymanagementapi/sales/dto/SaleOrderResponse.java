package com.portfolio.inventorymanagementapi.sales.dto;

import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderResponse {

    private Long id;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private SaleOrderStatus status;
    private List<SaleOrderItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;
    private boolean partialFill;
    private String notes;
    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
