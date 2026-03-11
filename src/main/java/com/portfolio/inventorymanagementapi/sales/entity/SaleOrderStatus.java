package com.portfolio.inventorymanagementapi.sales.entity;

public enum SaleOrderStatus {
    DRAFT,       // Order created but not confirmed — stock NOT deducted yet
    CONFIRMED,   // Order confirmed — stock IS deducted via FIFO
    SHIPPED,     // Order handed to shipping
    DELIVERED,   // Order received by customer
    CANCELLED    // Order cancelled — stock returned to batches
}