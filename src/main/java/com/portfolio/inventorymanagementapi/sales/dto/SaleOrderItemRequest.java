package com.portfolio.inventorymanagementapi.sales.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderItemRequest {

    /**
     * Identify product by ID OR by SKU — one of them must be provided.
     * ID takes priority if both are given.
     */
    private Long productId;

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String productSku;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @Size(max = 500)
    private String notes;

    // ── Validation helper ─────────────────────────────────────────────────────
    public boolean hasProductIdentifier() {
        return productId != null
                || (productSku != null && !productSku.isBlank());
    }
}