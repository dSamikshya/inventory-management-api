
package com.portfolio.inventorymanagementapi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents ONE ROW in the import file (CSV or Excel).
 *
 * How grouping works:
 *   Rows with the same orderReference + customerEmail are grouped into ONE order.
 *   Each row = one product line item on that order.
 *
 * Example file:
 * ┌───────────────┬──────────────────┬────────────┬──────────┬────────────┐
 * │ orderReference│ customerEmail    │ productSku │ quantity │ orderNotes │
 * ├───────────────┼──────────────────┼────────────┼──────────┼────────────┤
 * │ ORD-001       │ john@email.com   │ PHONE-BLK  │ 2        │ urgent     │
 * │ ORD-001       │ john@email.com   │ CASE-001   │ 3        │ urgent     │  ← same order
 * │ ORD-002       │ sara@email.com   │ LAPTOP-PRO │ 1        │            │
 * │ ORD-002       │ sara@email.com   │ MOUSE-USB  │ 5        │            │  ← different order
 * └───────────────┴──────────────────┴────────────┴──────────┴────────────┘
 *
 * Result: 2 orders created.
 *   ORD-001 for john: 2× PHONE-BLK + 3× CASE-001
 *   ORD-002 for sara: 1× LAPTOP-PRO + 5× MOUSE-USB
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleImportRequest {

    // ── Order grouping key ─────────────────────────────────────────────────────
    /** Groups rows into one order. e.g. "ORD-001". Required. */
    private String orderReference;

    // ── Customer (auto-create if not found by email) ───────────────────────────
    private String customerEmail;       // used to find or create customer
    private String customerFirstName;
    private String customerLastName;
    private String customerPhone;
    private String customerCity;
    private String customerCountry;

    // ── Product line — one per row ─────────────────────────────────────────────
    private Long   productId;           // use if you know the DB id
    private String productSku;          // use if you know the SKU (preferred)
    private Integer quantity;
    private String itemNotes;

    // ── Order-level fields (same for all rows of the same order) ──────────────
    private String orderNotes;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Key used to group rows into the same order.
     * Format: "email::reference" — both lowercased and trimmed.
     */
    public String getGroupKey() {
        String email = (customerEmail != null && !customerEmail.isBlank())
                ? customerEmail.toLowerCase().trim() : "walkin";
        String ref   = (orderReference != null && !orderReference.isBlank())
                ? orderReference.trim() : "default";
        return email + "::" + ref;
    }

    public boolean isValid() {
        boolean hasProduct = productId != null
                || (productSku != null && !productSku.isBlank());
        boolean hasQty = quantity != null && quantity > 0;
        return hasProduct && hasQty;
    }

    public String getValidationError() {
        if (productId == null && (productSku == null || productSku.isBlank()))
            return "productId or productSku is required for each row";
        if (quantity == null || quantity <= 0)
            return "quantity must be a positive number";
        return null;
    }
}
