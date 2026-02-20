package com.portfolio.inventorymanagementapi.product.controller;

import com.portfolio.inventorymanagementapi.product.dto.*;
import com.portfolio.inventorymanagementapi.product.service.ProductBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products/batches")
@RequiredArgsConstructor
@Tag(name = "Product Batches", description = "Batch management — single, bulk, CSV and Excel import")
@SecurityRequirement(name = "bearerAuth")
public class ProductBatchController {

    private final ProductBatchService productBatchService;

    // ==================== CREATE ====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Create a single batch",
            description = "Resolves product by productId → productSku → productName. " +
                    "Auto-creates product, category, and supplier if not found.")
    public ResponseEntity<ProductBatchResponse> createBatch(
            @Valid @RequestBody ProductBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productBatchService.createBatch(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Create multiple batches in one call",
            description = "Each item uses the same auto-resolution as single batch creation. " +
                    "Failures are skipped and reported in the response.")
    public ResponseEntity<BatchBulkResponse> createBatchesBulk(
            @Valid @RequestBody List<@Valid ProductBatchRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productBatchService.createBatchesBulk(requests));
    }

    // ==================== IMPORT ====================

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Import batches from a CSV file",
            description = "Required columns: (sku OR productName), category, quantity, costPrice, sellingPrice. " +
                    "Optional: description, categoryDescription, supplier, poNumber, expiryDate (YYYY-MM-DD), notes.")
    public ResponseEntity<BatchImportResponse> importFromCSV(
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.getOriginalFilename() == null ||
                    !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                throw new RuntimeException("File must be a .csv file");
            }
            return ResponseEntity.ok(productBatchService.importFromCSV(file));
        } catch (IOException e) {
            throw new RuntimeException("Error processing CSV file: " + e.getMessage());
        }
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Import batches from an Excel (.xlsx) file",
            description = "Required columns: (sku OR productName), category, quantity, costPrice, sellingPrice. " +
                    "Optional: description, categoryDescription, supplier, poNumber, expiryDate, notes.")
    public ResponseEntity<BatchImportResponse> importFromExcel(
            @RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
                throw new RuntimeException("File must be an .xlsx file");
            }
            return ResponseEntity.ok(productBatchService.importFromExcel(file));
        } catch (IOException e) {
            throw new RuntimeException("Error processing Excel file: " + e.getMessage());
        }
    }

    // ==================== READ ====================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Get all batches (paginated)")
    public ResponseEntity<Page<ProductBatchResponse>> getAllBatches(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(defaultValue = "id")   String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        return ResponseEntity.ok(productBatchService.getAllBatches(page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Get batch by ID")
    public ResponseEntity<ProductBatchResponse> getBatchById(@PathVariable Long id) {
        return ResponseEntity.ok(productBatchService.getBatchById(id));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Get all batches for a product")
    public ResponseEntity<List<ProductBatchResponse>> getBatchesByProduct(
            @PathVariable Long productId) {
        return ResponseEntity.ok(productBatchService.getBatchesByProduct(productId));
    }

    @GetMapping("/product/{productId}/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Get available (non-depleted) batches for a product, ordered FIFO")
    public ResponseEntity<List<ProductBatchResponse>> getAvailableBatches(
            @PathVariable Long productId) {
        return ResponseEntity.ok(productBatchService.getAvailableBatches(productId));
    }

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Get all batches from a specific supplier")
    public ResponseEntity<List<ProductBatchResponse>> getBatchesBySupplier(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(productBatchService.getBatchesBySupplier(supplierId));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Get batches expiring within N days (default 30)")
    public ResponseEntity<List<ProductBatchResponse>> getExpiringBatches(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(productBatchService.getExpiringBatches(days));
    }

    @GetMapping("/expired")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Get all expired batches that still have remaining stock")
    public ResponseEntity<List<ProductBatchResponse>> getExpiredBatches() {
        return ResponseEntity.ok(productBatchService.getExpiredBatches());
    }

    // ==================== UPDATE / DELETE ====================

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Update a batch")
    public ResponseEntity<ProductBatchResponse> updateBatch(
            @PathVariable Long id,
            @Valid @RequestBody ProductBatchRequest request) {
        return ResponseEntity.ok(productBatchService.updateBatch(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a batch (stock recalculated automatically via @Formula)")
    public ResponseEntity<Void> deleteBatch(@PathVariable Long id) {
        productBatchService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }
}