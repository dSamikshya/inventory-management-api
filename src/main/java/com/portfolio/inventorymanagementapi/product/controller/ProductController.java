package com.portfolio.inventorymanagementapi.product.controller;



import com.portfolio.inventorymanagementapi.product.dto.ProductBatchResponse;
import com.portfolio.inventorymanagementapi.product.dto.ProductBatchRequest;
import com.portfolio.inventorymanagementapi.product.dto.ProductRequest;
import com.portfolio.inventorymanagementapi.product.dto.ProductResponse;
import com.portfolio.inventorymanagementapi.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products", description = "Retrieve paginated list of all products")
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction
    ) {
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve a product by its ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU", description = "Retrieve a product by its SKU")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Search products by name, SKU, description, or barcode")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(productService.searchProducts(query, pageable));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get products by category", description = "Retrieve all products in a specific category")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, pageable));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock products", description = "Retrieve all products that are at or below reorder threshold")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    @GetMapping("/low-stock/count")
    @Operation(summary = "Get low stock count", description = "Get count of products at or below reorder threshold")
    public ResponseEntity<Long> getLowStockCount() {
        return ResponseEntity.ok(productService.getLowStockCount());
    }

    @PostMapping
    @Operation(summary = "Create new product", description = "Create a new product in the system")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Update an existing product")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Delete a product (only if no stock remaining)")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Batch endpoints
    @GetMapping("/{productId}/batches")
    @Operation(summary = "Get product batches", description = "Get all portfolio.inventorymanagementapi batches for a product")
    public ResponseEntity<List<ProductBatchResponse>> getProductBatches(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductBatches(productId));
    }

    @GetMapping("/{productId}/batches/available")
    @Operation(summary = "Get available batches", description = "Get all available portfolio.inventorymanagementapi batches for a product")
    public ResponseEntity<List<ProductBatchResponse>> getAvailableBatches(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getAvailableBatches(productId));
    }
    @PostMapping("/batches/bulk")
    @Operation(summary = "Create multiple product batches",
            description = "Add multiple inventory batches for products")
    public ResponseEntity<List<ProductBatchResponse>> createBatchesBulk(
            @Valid @RequestBody List<@Valid ProductBatchRequest> requests
    ) {
        List<ProductBatchResponse> created = productService.createBatchesBulk(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}