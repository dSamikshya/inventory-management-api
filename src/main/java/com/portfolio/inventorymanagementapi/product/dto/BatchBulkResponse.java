package com.portfolio.inventorymanagementapi.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchBulkResponse {

    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<ProductBatchResponse> successfulBatches;
    private List<BulkError> errors;

    public BatchBulkResponse(int totalRequested,
                             List<ProductBatchResponse> successfulBatches,
                             List<BulkError> errors) {
        this.totalRequested   = totalRequested;
        this.successfulBatches = successfulBatches;
        this.errors           = errors;
        this.successCount     = successfulBatches.size();
        this.failureCount     = errors.size();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkError {
        private int index;
        private String sku;
        private String productName;
        private String error;
    }
}
