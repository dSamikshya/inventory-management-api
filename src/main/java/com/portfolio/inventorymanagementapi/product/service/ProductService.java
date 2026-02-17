package com.portfolio.inventorymanagementapi.product.service;


import com.portfolio.inventorymanagementapi.product.dto.ProductBatchRequest;
import com.portfolio.inventorymanagementapi.product.dto.ProductBatchResponse;
import com.portfolio.inventorymanagementapi.product.dto.ProductRequest;
import com.portfolio.inventorymanagementapi.product.dto.ProductResponse;
import com.portfolio.inventorymanagementapi.product.entity.Category;
import com.portfolio.inventorymanagementapi.product.entity.Product;
import com.portfolio.inventorymanagementapi.product.entity.ProductBatch;
import com.portfolio.inventorymanagementapi.product.repository.CategoryRepository;
import com.portfolio.inventorymanagementapi.product.repository.ProductBatchRepository;
import com.portfolio.inventorymanagementapi.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductBatchRepository productBatchRepository;

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        log.debug("Fetching product with SKU: {}", sku);
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products with pagination");
        return productRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String search, Pageable pageable) {
        log.debug("Searching products with query: {}", search);
        return productRepository.searchProducts(search, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching products for category: {}", categoryId);
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        log.debug("Fetching low stock products");
        return productRepository.findLowStockProducts()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long getLowStockCount() {
        return productRepository.countLowStockProducts();
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating new product with SKU: {}", request.getSku());

        // Validation
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with SKU " + request.getSku() + " already exists");
        }

        if (request.getBarcode() != null && productRepository.existsByBarcode(request.getBarcode())) {
            throw new RuntimeException("Product with barcode " + request.getBarcode() + " already exists");
        }

        // Build product
        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .description(request.getDescription())
                .barcode(request.getBarcode())
                .weight(request.getWeight())
                .sellingPrice(request.getSellingPrice())
                .reorderThreshold(request.getReorderThreshold())
                .totalQuantity(0)
                .averageCostPrice(BigDecimal.ZERO)
                .build();

        // Set category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Product created successfully with id: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "products", allEntries = true)
    })
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.info("Updating product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Update fields
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSellingPrice(request.getSellingPrice());
        product.setReorderThreshold(request.getReorderThreshold());
        product.setWeight(request.getWeight());

        // Update barcode if changed
        if (request.getBarcode() != null && !request.getBarcode().equals(product.getBarcode())) {
            if (productRepository.existsByBarcode(request.getBarcode())) {
                throw new RuntimeException("Product with barcode " + request.getBarcode() + " already exists");
            }
            product.setBarcode(request.getBarcode());
        }

        // Update category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        Product updated = productRepository.save(product);
        log.info("Product updated successfully");

        return mapToResponse(updated);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Check if product has remaining stock
        if (product.getTotalQuantity() > 0) {
            throw new RuntimeException("Cannot delete product with remaining stock. Current quantity: " + product.getTotalQuantity());
        }

        productRepository.delete(product);
        log.info("Product deleted successfully");
    }

    // Batch management methods
    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getProductBatches(Long productId) {
        log.debug("Fetching batches for product: {}", productId);

        // Verify product exists
        productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        return productBatchRepository.findByProductId(productId)
                .stream()
                .map(this::mapBatchToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getAvailableBatches(Long productId) {
        log.debug("Fetching available batches for product: {}", productId);

        return productBatchRepository.findAvailableBatchesByProductId(productId)
                .stream()
                .map(this::mapBatchToResponse)
                .collect(Collectors.toList());
    }

    // Stock management
    @CacheEvict(value = "products", key = "#productId")
    public void updateStock(Long productId, Integer quantityChange) {
        log.info("Updating stock for product: {} by {}", productId, quantityChange);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        int newQuantity = product.getTotalQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new RuntimeException("Insufficient stock. Available: " + product.getTotalQuantity() + ", Required: " + Math.abs(quantityChange));
        }

        product.setTotalQuantity(newQuantity);
        productRepository.save(product);

        log.info("Stock updated. New quantity: {}", newQuantity);
    }

    @CacheEvict(value = "products", key = "#productId")
    public void updateAverageCostPrice(Long productId, BigDecimal newCostPrice, Integer quantity) {
        log.info("Updating average cost price for product: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        BigDecimal currentTotal = product.getAverageCostPrice().multiply(new BigDecimal(product.getTotalQuantity()));
        BigDecimal newTotal = newCostPrice.multiply(new BigDecimal(quantity));
        BigDecimal combinedTotal = currentTotal.add(newTotal);
        BigDecimal totalQuantity = new BigDecimal(product.getTotalQuantity() + quantity);

        BigDecimal newAverage = combinedTotal.divide(totalQuantity, 2, RoundingMode.HALF_UP);
        product.setAverageCostPrice(newAverage);

        productRepository.save(product);
        log.info("Average cost price updated to: {}", newAverage);
    }

    // Mapper methods
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .barcode(product.getBarcode())
                .weight(product.getWeight())
                .totalQuantity(product.getTotalQuantity())
                .reorderThreshold(product.getReorderThreshold())
                .averageCostPrice(product.getAverageCostPrice())
                .sellingPrice(product.getSellingPrice())
                .isLowStock(product.isLowStock())
                .batchCount(product.getBatches() != null ? product.getBatches().size() : 0)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private ProductBatchResponse mapBatchToResponse(ProductBatch batch) {
        return ProductBatchResponse.builder()
                .id(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .purchaseOrderId(batch.getPurchaseOrderId())
                .quantity(batch.getQuantity())
                .remainingQuantity(batch.getRemainingQuantity())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .expiryDate(batch.getExpiryDate())
                .notes(batch.getNotes())
                .isExpired(batch.isExpired())
                .isAvailable(batch.isAvailable())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
    @CacheEvict(value = "products", allEntries = true)
    public List<ProductBatchResponse> createBatchesBulk(List<ProductBatchRequest> requests) {
        log.info("Creating {} product batches in bulk", requests.size());

        List<ProductBatchResponse> createdBatches = new ArrayList<>();

        for (ProductBatchRequest request : requests) {
            try {
                // Get product
                Product product = productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));

                // Create batch
                ProductBatch batch = ProductBatch.builder()
                        .product(product)
                        .purchaseOrderId(request.getPurchaseOrderId())
                        .quantity(request.getQuantity())
                        .remainingQuantity(request.getQuantity())
                        .costPrice(request.getCostPrice())
                        .sellingPrice(request.getSellingPrice())
                        .expiryDate(request.getExpiryDate())
                        .notes(request.getNotes())
                        .build();

                ProductBatch saved = productBatchRepository.save(batch);

                // Update product stock and average cost
                updateStock(product.getId(), request.getQuantity());
                updateAverageCostPrice(product.getId(), request.getCostPrice(), request.getQuantity());

                createdBatches.add(mapBatchToResponse(saved));

            } catch (Exception e) {
                log.error("Error creating batch for product {}: {}", request.getProductId(), e.getMessage());
            }
        }

        log.info("Bulk batch create completed. Created: {}/{}", createdBatches.size(), requests.size());
        return createdBatches;
    }
}
