package com.portfolio.inventorymanagementapi.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderRequest {

    /**
     * Option A: provide existing customerId → uses that customer
     * Option B: leave customerId null, fill customer fields → auto-creates customer
     * Option C: leave everything null → walk-in order (no customer linked)
     */
    private Long customerId;

    // ── Auto-create customer fields (only used if customerId is null) ─────────
    @Size(max = 100)
    private String customerFirstName;

    @Size(max = 100)
    private String customerLastName;

    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String customerEmail;

    @Size(max = 20)
    private String customerPhone;

    @Size(max = 255)
    private String customerAddress;

    @Size(max = 100)
    private String customerCity;

    @Size(max = 50)
    private String customerState;

    @Size(max = 20)
    private String customerZipCode;

    @Size(max = 100)
    private String customerCountry;

    // ── Order fields ──────────────────────────────────────────────────────────
    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<SaleOrderItemRequest> items;

    @Size(max = 1000)
    private String notes;

    // ── Helper ────────────────────────────────────────────────────────────────
    /**
     * Returns true if customer details were provided for auto-creation.
     * Requires at least firstName OR email to create a customer.
     */
    public boolean hasCustomerDetails() {
        return customerId == null &&
                ((customerFirstName != null && !customerFirstName.isBlank()) ||
                        (customerEmail != null && !customerEmail.isBlank()));
    }
}