package com.portfolio.inventorymanagementapi.auth.entity;

public enum Role {
    ADMIN,      // Full system access
    MANAGER,    // Access to reports, products, sales, purchases
    SALES,      // Access to sales, customers, products (read-only)
    WAREHOUSE   // Access to products, purchase orders, inventory
}