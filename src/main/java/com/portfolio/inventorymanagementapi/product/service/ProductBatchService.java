package com.portfolio.inventorymanagementapi.product.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.portfolio.inventorymanagementapi.product.dto.*;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBatchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductBatchRepository productBatchRepository;
    private final SupplierService supplierService;

    // ==================== SINGLE BATCH ====================

    /**
     * Create a single batch.
     * Resolves product by: productId → productSku → productName (auto-creates if not found).
     * Category and supplier are also auto-created if not found.
     */
    @Transactional
    public ProductBatchResponse createBatch(ProductBatchRequest request) {
        log.info("Creating batch — productId={}, sku={}, name={}",
                request.getProductId(), request.getProductSku(), request.getProductName());

        Category category = resolveOrCreateCategory(
                request.getCategoryName(), request.getCategoryDescription());
        Supplier supplier = supplierService.findOrCreateSupplier(request.getSupplierName());
        Product product = resolveOrCreateProduct(request, category);

        ProductBatch batch = buildAndSaveBatch(request, product, supplier);

        log.info("Batch created: id={}, sku={}, qty={}",
                batch.getId(), product.getSku(), batch.getQuantity());

        return mapToResponse(batch);
    }

    // ==================== BULK ====================

    /**
     * Create multiple batches in one call.
     * Each item uses the same auto-resolution as createBatch().
     * Failures are logged and skipped — successful ones are still saved.
     */
    @Transactional
    public BatchBulkResponse createBatchesBulk(List<ProductBatchRequest> requests) {
        log.info("Bulk creating {} batches", requests.size());

        List<ProductBatchResponse> successes = new ArrayList<>();
        List<BatchBulkResponse.BulkError> errors = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            try {
                successes.add(createBatch(requests.get(i)));
            } catch (Exception e) {
                log.error("Bulk item {} failed: {}", i + 1, e.getMessage());
                errors.add(new BatchBulkResponse.BulkError(
                        i + 1,
                        requests.get(i).getProductSku(),
                        requests.get(i).getProductName(),
                        e.getMessage()));
            }
        }

        log.info("Bulk complete: {}/{} succeeded", successes.size(), requests.size());
        return new BatchBulkResponse(requests.size(), successes, errors);
    }

    // ==================== CSV IMPORT ====================

    /**
     * Import batches from a CSV file.
     *
     * Required columns : sku OR productName, category, quantity, costPrice, sellingPrice
     * Optional columns : description, categoryDescription, supplier, poNumber,
     *                    expiryDate (YYYY-MM-DD), notes
     */
    @Transactional
    public BatchImportResponse importFromCSV(MultipartFile file) throws IOException {
        log.info("Importing batches from CSV: {}", file.getOriginalFilename());
        BatchImportResponse response = new BatchImportResponse();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) throw new RuntimeException("CSV file is empty");

            Map<String, Integer> col = mapHeaders(rows.get(0));
            response.setTotalRows(rows.size() - 1);

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    response.addSuccess(createBatch(parseCSVRow(row, col)));
                } catch (Exception e) {
                    log.warn("CSV row {} failed: {}", i + 1, e.getMessage());
                    response.addError(i + 1,
                            safeGetCol(row, col, "sku"),
                            safeGetCol(row, col, "productname"),
                            e.getMessage());
                }
            }
        } catch (CsvException e) {
            throw new RuntimeException("Error reading CSV file: " + e.getMessage());
        }

        log.info(response.getSummary());
        return response;
    }

    // ==================== EXCEL IMPORT ====================

    /**
     * Import batches from an Excel (.xlsx) file.
     *
     * Required columns : sku OR productName, category, quantity, costPrice, sellingPrice
     * Optional columns : description, categoryDescription, supplier, poNumber,
     *                    expiryDate, notes
     */
    @Transactional
    public BatchImportResponse importFromExcel(MultipartFile file) throws IOException {
        log.info("Importing batches from Excel: {}", file.getOriginalFilename());
        BatchImportResponse response = new BatchImportResponse();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0)
                throw new RuntimeException("Excel file is empty");

            Map<String, Integer> col = mapExcelHeaders(sheet.getRow(0));
            response.setTotalRows(sheet.getPhysicalNumberOfRows() - 1);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    response.addSuccess(createBatch(parseExcelRow(row, col)));
                } catch (Exception e) {
                    log.warn("Excel row {} failed: {}", i + 1, e.getMessage());
                    response.addError(i + 1,
                            getCellString(row, col.get("sku")),
                            getCellString(row, col.get("productname")),
                            e.getMessage());
                }
            }
        }

        log.info(response.getSummary());
        return response;
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public ProductBatchResponse getBatchById(Long id) {
        return mapToResponse(findBatchById(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductBatchResponse> getAllBatches(int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        return productBatchRepository.findAll(PageRequest.of(page, size, sort))
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getBatchesByProduct(Long productId) {
        return productBatchRepository.findByProductId(productId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getAvailableBatches(Long productId) {
        return productBatchRepository.findAvailableBatchesByProductId(productId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getExpiringBatches(int daysAhead) {
        return productBatchRepository.findBatchesExpiringBefore(LocalDateTime.now().plusDays(daysAhead))
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getExpiredBatches() {
        return productBatchRepository.findExpiredBatches()
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductBatchResponse> getBatchesBySupplier(Long supplierId) {
        return productBatchRepository.findBySupplierId(supplierId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ==================== UPDATE / DELETE ====================

    @Transactional
    public ProductBatchResponse updateBatch(Long id, ProductBatchRequest request) {
        ProductBatch batch = findBatchById(id);

        batch.setQuantity(request.getQuantity());
        batch.setRemainingQuantity(request.getQuantity());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());
        batch.setExpiryDate(request.getExpiryDate() != null
                ? request.getExpiryDate().atStartOfDay() : null);
        batch.setNotes(request.getNotes());
        batch.setPurchaseOrderId(request.getPurchaseOrderId());

        if (request.getSupplierName() != null) {
            batch.setSupplier(supplierService.findOrCreateSupplier(request.getSupplierName()));
        }

        return mapToResponse(productBatchRepository.save(batch));
    }

    @Transactional
    public void deleteBatch(Long id) {
        productBatchRepository.delete(findBatchById(id));
        log.info("Batch deleted: id={}", id);
    }

    // ==================== PRIVATE HELPERS ====================

    private Category resolveOrCreateCategory(String name, String description) {
        if (name == null || name.isBlank()) return null;
        return categoryRepository.findByName(name)
                .orElseGet(() -> {
                    log.info("Auto-creating category: {}", name);
                    Category c = new Category();
                    c.setName(name);
                    c.setDescription(description != null ? description : "Auto-created");
                    return categoryRepository.save(c);
                });
    }

    private Product resolveOrCreateProduct(ProductBatchRequest request, Category category) {
        // 1. By ID
        if (request.getProductId() != null) {
            return productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found with id: " + request.getProductId()));
        }
        // 2. By SKU
        if (request.getProductSku() != null && !request.getProductSku().isBlank()) {
            Optional<Product> found = productRepository.findBySku(request.getProductSku());
            if (found.isPresent()) return found.get();
        }
        // 3. By name
        if (request.getProductName() != null && !request.getProductName().isBlank()) {
            Optional<Product> found = productRepository.findByName(request.getProductName());
            if (found.isPresent()) return found.get();
        }
        // 4. Create
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new RuntimeException("Product name is required when creating a new product");
        }
        if (category == null) {
            throw new RuntimeException("Category name is required when creating a new product");
        }

        log.info("Auto-creating product: {}", request.getProductName());
        Product product = Product.builder()
                .sku(request.getProductSku() != null && !request.getProductSku().isBlank()
                        ? request.getProductSku()
                        : generateSku(request.getProductName()))
                .name(request.getProductName())
                .description(request.getProductDescription())
                .category(category)
                .sellingPrice(request.getSellingPrice())
                .build();
        return productRepository.save(product);
    }

    private ProductBatch buildAndSaveBatch(ProductBatchRequest request,
                                           Product product, Supplier supplier) {
        return productBatchRepository.save(ProductBatch.builder()
                .product(product)
                .supplier(supplier)
                .purchaseOrderId(request.getPurchaseOrderId())
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .expiryDate(request.getExpiryDate() != null
                        ? request.getExpiryDate().atStartOfDay() : null)
                .notes(request.getNotes())
                .build());
    }

    private ProductBatch findBatchById(Long id) {
        return productBatchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with id: " + id));
    }

    private String generateSku(String productName) {
        String base = productName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "-").replaceAll("-+", "-");
        if (base.length() > 20) base = base.substring(0, 20);
        return base.replaceAll("-$", "") + "-" + System.currentTimeMillis();
    }

    // ==================== CSV PARSING ====================

    private Map<String, Integer> mapHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++)
            map.put(headers[i].toLowerCase().trim().replaceAll("\\s+", ""), i);
        return map;
    }

    private ProductBatchRequest parseCSVRow(String[] row, Map<String, Integer> col) {
        ProductBatchRequest req = new ProductBatchRequest();
        req.setProductSku(         getCol(row, col, "sku"));
        req.setProductName(        getCol(row, col, "productname", "name"));
        req.setProductDescription( getCol(row, col, "description", "desc"));
        req.setCategoryName(       getCol(row, col, "category", "categoryname"));
        req.setCategoryDescription(getCol(row, col, "categorydescription", "categorydesc"));
        req.setSupplierName(       getCol(row, col, "supplier", "suppliername"));
        req.setPurchaseOrderId(    getCol(row, col, "ponumber", "po", "purchaseorder"));
        req.setNotes(              getCol(row, col, "notes"));

        try {
            String qty  = getCol(row, col, "quantity", "qty");
            String cost = getCol(row, col, "costprice", "cost");
            String sell = getCol(row, col, "sellingprice", "price", "sell");
            String exp  = getCol(row, col, "expirydate", "expiry");

            if (qty  != null) req.setQuantity(Integer.parseInt(qty));
            if (cost != null) req.setCostPrice(new BigDecimal(cost));
            if (sell != null) req.setSellingPrice(new BigDecimal(sell));
            if (exp  != null && !exp.isBlank()) req.setExpiryDate(LocalDate.parse(exp));
        } catch (Exception e) {
            throw new RuntimeException("Invalid number/date format: " + e.getMessage());
        }
        return req;
    }

    private String getCol(String[] row, Map<String, Integer> col, String... names) {
        for (String name : names) {
            Integer idx = col.get(name.toLowerCase());
            if (idx != null && idx < row.length && !row[idx].isBlank())
                return row[idx].trim();
        }
        return null;
    }

    private String safeGetCol(String[] row, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        return (idx != null && idx < row.length) ? row[idx] : "";
    }

    // ==================== EXCEL PARSING ====================

    private Map<String, Integer> mapExcelHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow)
            map.put(cell.getStringCellValue().toLowerCase().trim().replaceAll("\\s+", ""),
                    cell.getColumnIndex());
        return map;
    }

    private ProductBatchRequest parseExcelRow(Row row, Map<String, Integer> col) {
        ProductBatchRequest req = new ProductBatchRequest();
        req.setProductSku(         getCellString(row, col.get("sku")));
        req.setProductName(        getCellString(row, col.get("productname")));
        req.setProductDescription( getCellString(row, col.get("description")));
        req.setCategoryName(       getCellString(row, col.get("category")));
        req.setCategoryDescription(getCellString(row, col.get("categorydescription")));
        req.setSupplierName(       getCellString(row, col.get("supplier")));
        req.setPurchaseOrderId(    getCellString(row, col.get("ponumber")));
        req.setNotes(              getCellString(row, col.get("notes")));

        Integer qty = getCellInt(row, col.get("quantity"));
        if (qty != null) req.setQuantity(qty);

        BigDecimal cost = getCellDecimal(row, col.get("costprice"));
        if (cost != null) req.setCostPrice(cost);

        BigDecimal sell = getCellDecimal(row, col.get("sellingprice"));
        if (sell != null) req.setSellingPrice(sell);

        LocalDate expiry = getCellDate(row, col.get("expirydate"));
        if (expiry != null) req.setExpiryDate(expiry);

        return req;
    }

    private String getCellString(Row row, Integer idx) {
        if (idx == null || idx < 0) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    private Integer getCellInt(Row row, Integer idx) {
        if (idx == null || idx < 0) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING  -> Integer.parseInt(cell.getStringCellValue().trim());
            default      -> null;
        };
    }

    private BigDecimal getCellDecimal(Row row, Integer idx) {
        if (idx == null || idx < 0) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> new BigDecimal(cell.getStringCellValue().trim());
            default      -> null;
        };
    }

    private LocalDate getCellDate(Row row, Integer idx) {
        if (idx == null || idx < 0) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (cell.getCellType() == CellType.STRING) {
            try { return LocalDate.parse(cell.getStringCellValue().trim()); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    // ==================== MAPPING ====================

    public ProductBatchResponse mapToResponse(ProductBatch batch) {
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