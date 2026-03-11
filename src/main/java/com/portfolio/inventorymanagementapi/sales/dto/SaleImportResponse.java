package com.portfolio.inventorymanagementapi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a bulk sales import operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleImportResponse {

    private int totalRows;
    private int totalOrdersCreated;
    private int totalItemsCreated;
    private int failureCount;

    private List<SaleOrderResponse> createdOrders = new ArrayList<>();
    private List<ImportError>       errors        = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        private int    row;
        private String orderReference;
        private String customerEmail;
        private String productSku;
        private String error;
    }

    public void addOrder(SaleOrderResponse order) {
        createdOrders.add(order);
        totalOrdersCreated++;
        totalItemsCreated += (order.getItems() != null) ? order.getItems().size() : 0;
    }

    public void addError(int row, String orderReference,
                         String customerEmail, String productSku, String error) {
        errors.add(new ImportError(row, orderReference, customerEmail, productSku, error));
        failureCount++;
    }

    public String getSummary() {
        return String.format(
                "Import complete: %d orders created with %d total items. %d rows failed.",
                totalOrdersCreated, totalItemsCreated, failureCount);
    }
}