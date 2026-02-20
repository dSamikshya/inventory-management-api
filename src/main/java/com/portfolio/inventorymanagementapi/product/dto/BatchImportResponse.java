package com.portfolio.inventorymanagementapi.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for batch import operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResponse {

    private int totalRows;
    private int successCount;
    private int failureCount;
    private int categoriesCreated;
    private int productsCreated;
    private int batchesCreated;

    private List<ImportError> errors = new ArrayList<>();
    private List<ProductBatchResponse> successfulBatches = new ArrayList<>();

    /**
     * Individual import error
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        private int row;
        private String sku;
        private String productName;
        private String error;
    }

    /**
     * Add successful batch
     */
    public void addSuccess(ProductBatchResponse batch) {
        successfulBatches.add(batch);
        successCount++;
    }

    /**
     * Add error
     */
    public void addError(int row, String sku, String productName, String error) {
        errors.add(new ImportError(row, sku, productName, error));
        failureCount++;
    }

    /**
     * Get summary message
     */
    public String getSummary() {
        return String.format(
                "Import completed: %d/%d successful, %d failed. " +
                        "Created: %d categories, %d products, %d batches",
                successCount, totalRows, failureCount,
                categoriesCreated, productsCreated, batchesCreated
        );
    }
}