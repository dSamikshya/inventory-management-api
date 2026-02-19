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
import com.portfolio.inventorymanagementapi.supplier.entity.Supplier;
import com.portfolio.inventorymanagementapi.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductBatchRepository productBatchRepository;
    private final SupplierService supplierService;

    // ==================== PRODUCT CRUD ====================

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with SKU '" + request.getSku() + "' already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .description(request.getDescription())
                .barcode(request.getBarcode())
                .weight(request.getWeight())
                .sellingPrice(request.getSellingPrice())
                .reorderThreshold(request.getReorderThreshold() != null ? request.getReorderThreshold() : 10)
                .build();

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String searchTerm, Pageable pageable) {
        return productRepository.searchProducts(searchTerm, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getLowStockCount() {
        return productRepository.countLowStockProducts();
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductById(id);

        // SKU is immutable after creation — skip if not changing
        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with SKU '" + request.getSku() + "' already exists");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSellingPrice(request.getSellingPrice());
        product.setReorderThreshold(request.getReorderThreshold() != null ? request.getReorderThreshold() : 10);
        product.setWeight(request.getWeight());
        product.setBarcode(request.getBarcode());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        Product product = findProductById(id);

        Integer qty = product.getTotalQuantity();
        if (qty != null && qty > 0) {
            throw new RuntimeException(
                    "Cannot delete product with remaining stock (" + qty + " units). " +
                            "Clear all batches first.");
        }

        productRepository.delete(product);
        log.info("Product deleted: id={}, sku={}", id, product.getSku());
    }

    // ==================== BATCH CRUD ====================

    /**
     * Create a batch for an existing product (productId required).
     */
    @Transactional
    public ProductBatchResponse createProductBatch(ProductBatchRequest request) {
        if (request.getProductId() == null) {
            throw new RuntimeException("productId is required. Use the /smart endpoint to auto-create products.");
        }

        Product product = findProductById(request.getProductId());
        Supplier supplier = supplierService.findOrCreateSupplier(request.getSupplierName());

        ProductBatch batch = buildBatch(request, product, supplier);
        return mapBatchToResponse(productBatchRepository.save(batch));
    }

    /**
     * Smart batch creation — resolves or creates product, category, and supplier automatically.
     */
    @Transactional
    public ProductBatchResponse createBatchSmart(ProductBatchRequest request) {
        Product product = resolveOrCreateProduct(request);
        Supplier supplier = supplierService.findOrCreateSupplier(request.getSupplierName());

        ProductBatch batch = buildBatch(request, product, supplier);
        ProductBatch saved = productBatchRepository.save(batch);

        log.info("Smart batch created: batchId={}, productSku={}, qty={}",
                saved.getId(), product.getSku(), saved.getQuantity());

        return mapBatchToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ProductBatchResponse> getAllBatches(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return productBatchRepository.findAll(pageable).map(this::mapBatchToResponse);
    }

    @Transactional(readOnly = true)
    public ProductBatchResponse getProductBatchById(Long batchId) {
        return mapBatchToResponse(findBatchById(batchId));
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getBatchesByProduct(Long productId) {
        return productBatchRepository.findByProductId(productId)
                .stream().map(this::mapBatchToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getAvailableBatches(Long productId) {
        return productBatchRepository.findAvailableBatchesByProductId(productId)
                .stream().map(this::mapBatchToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getExpiringBatches(int daysAhead) {
        LocalDateTime targetDate = LocalDateTime.now().plusDays(daysAhead);
        return productBatchRepository.findBatchesExpiringBefore(targetDate)
                .stream().map(this::mapBatchToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getExpiredBatches() {
        return productBatchRepository.findExpiredBatches()
                .stream().map(this::mapBatchToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getBatchesBySupplier(Long supplierId) {
        return productBatchRepository.findBySupplierId(supplierId)
                .stream().map(this::mapBatchToResponse).collect(Collectors.toList());
    }

    /**
     * Update a batch — only metadata fields are editable.
     * Quantity changes are reflected immediately via @Formula on the product.
     */
    @Transactional
    public ProductBatchResponse updateProductBatch(Long batchId, ProductBatchRequest request) {
        ProductBatch batch = findBatchById(batchId);

        batch.setQuantity(request.getQuantity());
        batch.setRemainingQuantity(request.getQuantity());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());
        batch.setExpiryDate(request.getExpiryDate() != null ? request.getExpiryDate().atStartOfDay() : null);
        batch.setNotes(request.getNotes());
        batch.setPurchaseOrderId(request.getPurchaseOrderId());

        if (request.getSupplierName() != null) {
            batch.setSupplier(supplierService.findOrCreateSupplier(request.getSupplierName()));
        }

        return mapBatchToResponse(productBatchRepository.save(batch));
    }

    @Transactional
    public void deleteProductBatch(Long batchId) {
        ProductBatch batch = findBatchById(batchId);
        // Stock is automatically recalculated via @Formula after deletion
        productBatchRepository.delete(batch);
        log.info("Batch deleted: id={}", batchId);
    }

    @Transactional
    public List<ProductBatchResponse> createBatchesBulk(List<ProductBatchRequest> requests) {
        List<ProductBatchResponse> responses = new ArrayList<>();
        for (ProductBatchRequest request : requests) {
            try {
                responses.add(createProductBatch(request));
            } catch (Exception e) {
                log.error("Failed to create batch for productId={}: {}", request.getProductId(), e.getMessage());
            }
        }
        return responses;
    }

    // ==================== INTERNAL HELPERS ====================

    /**
     * Resolve product by id → sku → name, or create a new one.
     */
    private Product resolveOrCreateProduct(ProductBatchRequest request) {
        if (request.getProductId() != null) {
            return findProductById(request.getProductId());
        }

        if (request.getProductSku() != null && !request.getProductSku().isBlank()) {
            Optional<Product> bySku = productRepository.findBySku(request.getProductSku());
            if (bySku.isPresent()) {
                log.debug("Found existing product by SKU: {}", request.getProductSku());
                return bySku.get();
            }
        }

        if (request.getProductName() != null && !request.getProductName().isBlank()) {
            Optional<Product> byName = productRepository.findByName(request.getProductName());
            if (byName.isPresent()) {
                log.debug("Found existing product by name: {}", request.getProductName());
                return byName.get();
            }
        }

        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new RuntimeException("Product name is required when creating a new product");
        }

        return createProductFromBatchRequest(request);
    }

    private Product createProductFromBatchRequest(ProductBatchRequest request) {
        if (request.getCategoryName() == null || request.getCategoryName().isBlank()) {
            throw new RuntimeException("Category name is required when creating a new product");
        }

        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseGet(() -> {
                    log.info("Auto-creating category: {}", request.getCategoryName());
                    Category c = new Category();
                    c.setName(request.getCategoryName());
                    c.setDescription(request.getCategoryDescription() != null
                            ? request.getCategoryDescription()
                            : "Auto-created from batch import");
                    return categoryRepository.save(c);
                });

        Product product = Product.builder()
                .sku(request.getProductSku() != null && !request.getProductSku().isBlank()
                        ? request.getProductSku()
                        : generateSku(request.getProductName()))
                .name(request.getProductName())
                .description(request.getProductDescription())
                .category(category)
                .sellingPrice(request.getSellingPrice())
                .build();

        Product saved = productRepository.save(product);
        log.info("Auto-created product: sku={}, name={}", saved.getSku(), saved.getName());
        return saved;
    }

    private ProductBatch buildBatch(ProductBatchRequest request, Product product, Supplier supplier) {
        return ProductBatch.builder()
                .product(product)
                .supplier(supplier)
                .purchaseOrderId(request.getPurchaseOrderId())
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .expiryDate(request.getExpiryDate() != null ? request.getExpiryDate().atStartOfDay() : null)
                .notes(request.getNotes())
                .build();
    }

    private String generateSku(String productName) {
        String base = productName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "-")
                .replaceAll("-+", "-");
        if (base.length() > 20) base = base.substring(0, 20);
        return base.replaceAll("-$", "") + "-" + System.currentTimeMillis();
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    private ProductBatch findBatchById(Long id) {
        return productBatchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with id: " + id));
    }

    // ==================== MAPPING ====================

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
                .averageCostPrice(product.getAverageCostPrice())
                .sellingPrice(product.getSellingPrice())
                .reorderThreshold(product.getReorderThreshold())
                .active(product.isActive())
                .isLowStock(product.isLowStock())
                .batchCount(product.getBatches() != null ? product.getBatches().size() : 0)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public ProductBatchResponse mapBatchToResponse(ProductBatch batch) {
        return ProductBatchResponse.builder()
                .id(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .productSku(batch.getProduct().getSku())
                .categoryName(batch.getProduct().getCategory() != null
                        ? batch.getProduct().getCategory().getName() : null)
                .supplierId(batch.getSupplier() != null ? batch.getSupplier().getId() : null)
                .supplierName(batch.getSupplier() != null ? batch.getSupplier().getName() : null)
                .purchaseOrderId(batch.getPurchaseOrderId())
                .quantity(batch.getQuantity())
                .remainingQuantity(batch.getRemainingQuantity())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .remainingValue(batch.getRemainingValue())
                .expiryDate(batch.getExpiryDate())
                .notes(batch.getNotes())
                .isExpired(batch.isExpired())
                .isAvailable(batch.isAvailable())
                .isDepleted(batch.isDepleted())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}