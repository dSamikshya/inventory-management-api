package com.portfolio.inventorymanagementapi.customer.entity;

public enum CustomerStatus {
    ACTIVE,      // Currently doing business
    INACTIVE,    // Not currently purchasing
    BLOCKED      // Cannot make purchases (payment issues, disputes, etc.)
}