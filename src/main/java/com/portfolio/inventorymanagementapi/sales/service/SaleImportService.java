package com.portfolio.inventorymanagementapi.sales.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.portfolio.inventorymanagementapi.sales.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles bulk sales order import from CSV and Excel files.
 *
 * GROUPING LOGIC:
 *   Each row = one product line item.
 *   Rows with the same (orderReference + customerEmail) = one order with multiple items.
 *
 * Example:
 *   ORD-001 | john@email.com | PHONE-BLK  | 2  → \
 *   ORD-001 | john@email.com | CASE-001   | 3  →  one order, 2 items
 *   ORD-002 | sara@email.com | LAPTOP-PRO | 1  → \
 *   ORD-002 | sara@email.com | MOUSE-USB  | 5  →  different order, 2 items
 *
 * REQUIRED COLUMNS (case-insensitive, spaces ignored):
 *   orderReference, productSku (or productId), quantity
 *
 * OPTIONAL COLUMNS:
 *   customerEmail, customerFirstName, customerLastName, customerPhone,
 *   customerCity, customerCountry, itemNotes, orderNotes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleImportService {

    private final SaleOrderService saleOrderService;

    // ==================== CSV ====================

    @Transactional
    public SaleImportResponse importFromCSV(MultipartFile file) throws IOException {
        log.info("Importing sales from CSV: {}", file.getOriginalFilename());
        SaleImportResponse response = new SaleImportResponse();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) throw new RuntimeException("CSV file is empty");

            Map<String, Integer> col = normaliseHeaders(rows.get(0));
            response.setTotalRows(rows.size() - 1);

            List<ParsedRow> parsed = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                try {
                    SaleImportRequest req = parseCSVRow(rows.get(i), col);
                    if (!req.isValid()) {
                        response.addError(i + 1, req.getOrderReference(),
                                req.getCustomerEmail(), req.getProductSku(),
                                req.getValidationError());
                    } else {
                        parsed.add(new ParsedRow(i + 1, req));
                    }
                } catch (Exception e) {
                    response.addError(i + 1, "", "", "", "Parse error: " + e.getMessage());
                }
            }
            buildOrders(parsed, response);

        } catch (CsvException e) {
            throw new RuntimeException("Error reading CSV: " + e.getMessage());
        }

        log.info(response.getSummary());
        return response;
    }

    // ==================== EXCEL ====================

    @Transactional
    public SaleImportResponse importFromExcel(MultipartFile file) throws IOException {
        log.info("Importing sales from Excel: {}", file.getOriginalFilename());
        SaleImportResponse response = new SaleImportResponse();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0)
                throw new RuntimeException("Excel file is empty");

            Map<String, Integer> col = normaliseExcelHeaders(sheet.getRow(0));
            response.setTotalRows(sheet.getPhysicalNumberOfRows() - 1);

            List<ParsedRow> parsed = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    SaleImportRequest req = parseExcelRow(row, col);
                    if (!req.isValid()) {
                        response.addError(i + 1, req.getOrderReference(),
                                req.getCustomerEmail(), req.getProductSku(),
                                req.getValidationError());
                    } else {
                        parsed.add(new ParsedRow(i + 1, req));
                    }
                } catch (Exception e) {
                    response.addError(i + 1, "", "", "", "Parse error: " + e.getMessage());
                }
            }
            buildOrders(parsed, response);
        }

        log.info(response.getSummary());
        return response;
    }

    // ==================== GROUPING & ORDER CREATION ====================

    /**
     * Groups parsed rows by (customerEmail + orderReference) then calls
     * SaleOrderService.createOrder() once per group.
     */
    private void buildOrders(List<ParsedRow> rows, SaleImportResponse response) {
        // LinkedHashMap preserves file order
        Map<String, List<ParsedRow>> groups = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.req.getGroupKey(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        log.info("Grouped {} rows into {} orders", rows.size(), groups.size());

        for (Map.Entry<String, List<ParsedRow>> entry : groups.entrySet()) {
            List<ParsedRow> group = entry.getValue();
            SaleImportRequest first = group.get(0).req;

            try {
                SaleOrderRequest orderReq = toOrderRequest(first, group);
                SaleOrderResponse created = saleOrderService.createOrder(orderReq);
                response.addOrder(created);
                log.info("Created {} for customer={} items={}",
                        created.getInvoiceNumber(), first.getCustomerEmail(), group.size());
            } catch (Exception e) {
                log.error("Failed group {}: {}", entry.getKey(), e.getMessage());
                for (ParsedRow pr : group) {
                    response.addError(pr.rowNum,
                            pr.req.getOrderReference(),
                            pr.req.getCustomerEmail(),
                            pr.req.getProductSku(),
                            e.getMessage());
                }
            }
        }
    }

    /**
     * Converts a group of rows (same order) into a SaleOrderRequest.
     * First row supplies the customer details; each row supplies one item.
     */
    private SaleOrderRequest toOrderRequest(SaleImportRequest first, List<ParsedRow> group) {
        // Build items
        List<SaleOrderItemRequest> items = group.stream()
                .map(pr -> {
                    SaleOrderItemRequest item = new SaleOrderItemRequest();
                    item.setProductId(pr.req.getProductId());
                    item.setProductSku(pr.req.getProductSku());
                    item.setQuantity(pr.req.getQuantity());
                    item.setNotes(pr.req.getItemNotes());
                    return item;
                })
                .collect(Collectors.toList());

        SaleOrderRequest req = new SaleOrderRequest();

        // Customer — taken from first row of the group
        req.setCustomerEmail(first.getCustomerEmail());
        req.setCustomerFirstName(first.getCustomerFirstName());
        req.setCustomerLastName(first.getCustomerLastName());
        req.setCustomerPhone(first.getCustomerPhone());
        req.setCustomerCity(first.getCustomerCity());
        req.setCustomerCountry(first.getCustomerCountry());

        req.setItems(items);

        // Append the order reference into the notes for traceability
        String ref = first.getOrderReference() != null ? first.getOrderReference() : "";
        req.setNotes(first.getOrderNotes() != null
                ? first.getOrderNotes() + " [ref: " + ref + "]"
                : "[ref: " + ref + "]");

        return req;
    }

    // ==================== CSV PARSING ====================

    private Map<String, Integer> normaliseHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++)
            map.put(headers[i].toLowerCase().replaceAll("\\s+", "").trim(), i);
        return map;
    }

    private SaleImportRequest parseCSVRow(String[] row, Map<String, Integer> col) {
        SaleImportRequest req = new SaleImportRequest();
        req.setOrderReference(   csv(row, col, "orderreference", "reference", "order"));
        req.setCustomerEmail(    csv(row, col, "customeremail",  "email"));
        req.setCustomerFirstName(csv(row, col, "customerfirstname", "firstname"));
        req.setCustomerLastName( csv(row, col, "customerlastname",  "lastname"));
        req.setCustomerPhone(    csv(row, col, "customerphone",  "phone"));
        req.setCustomerCity(     csv(row, col, "customercity",   "city"));
        req.setCustomerCountry(  csv(row, col, "customercountry","country"));
        req.setProductSku(       csv(row, col, "productsku",     "sku"));
        req.setItemNotes(        csv(row, col, "itemnotes",      "notes"));
        req.setOrderNotes(       csv(row, col, "ordernotes"));

        String pid = csv(row, col, "productid");
        if (pid != null) {
            try { req.setProductId(Long.parseLong(pid)); } catch (Exception ignored) {}
        }

        String qty = csv(row, col, "quantity", "qty");
        if (qty != null) {
            try { req.setQuantity(Integer.parseInt(qty)); }
            catch (Exception e) { throw new RuntimeException("Invalid quantity: " + qty); }
        }
        return req;
    }

    private String csv(String[] row, Map<String, Integer> col, String... names) {
        for (String name : names) {
            Integer idx = col.get(name.toLowerCase());
            if (idx != null && idx < row.length) {
                String val = row[idx].trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    // ==================== EXCEL PARSING ====================

    private Map<String, Integer> normaliseExcelHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow)
            map.put(cell.getStringCellValue().toLowerCase().replaceAll("\\s+", "").trim(),
                    cell.getColumnIndex());
        return map;
    }

    private SaleImportRequest parseExcelRow(Row row, Map<String, Integer> col) {
        SaleImportRequest req = new SaleImportRequest();
        req.setOrderReference(   xls(row, col, "orderreference"));
        req.setCustomerEmail(    xls(row, col, "customeremail"));
        req.setCustomerFirstName(xls(row, col, "customerfirstname"));
        req.setCustomerLastName( xls(row, col, "customerlastname"));
        req.setCustomerPhone(    xls(row, col, "customerphone"));
        req.setCustomerCity(     xls(row, col, "customercity"));
        req.setCustomerCountry(  xls(row, col, "customercountry"));
        req.setProductSku(       xls(row, col, "productsku"));
        req.setItemNotes(        xls(row, col, "itemnotes"));
        req.setOrderNotes(       xls(row, col, "ordernotes"));

        Integer pid = xlsInt(row, col, "productid");
        if (pid != null) req.setProductId(pid.longValue());
        req.setQuantity(xlsInt(row, col, "quantity"));
        return req;
    }

    private String xls(Row row, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> {
                String v = cell.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    private Integer xlsInt(Row row, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING  -> {
                try { yield Integer.parseInt(cell.getStringCellValue().trim()); }
                catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    // ── Inner helper ────────────────────────────────────────────────────────
    private record ParsedRow(int rowNum, SaleImportRequest req) {}
}