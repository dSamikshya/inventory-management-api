package com.portfolio.inventorymanagementapi.sales.controller;

import com.portfolio.inventorymanagementapi.sales.dto.*;
import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderStatus;
import com.portfolio.inventorymanagementapi.sales.service.SaleImportService;
import com.portfolio.inventorymanagementapi.sales.service.SaleOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Sales Orders", description = "Sales order management — FIFO stock deduction, multi-item orders, bulk CSV/Excel import")
@SecurityRequirement(name = "bearerAuth")
public class SaleOrderController {

    private final SaleOrderService  saleOrderService;
    private final SaleImportService saleImportService;

    // ==================== CREATE ====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(
            summary = "Create a new sale order",
            description = """
            Creates an order in DRAFT status. Stock is NOT deducted until the order is confirmed.

            Customer options:
            - Pass `customerId` to link an existing customer
            - Pass `customerEmail` + name fields to auto-create a new customer
            - Pass nothing → walk-in order (no customer linked)

            Product options (per item):
            - Pass `productId` to reference by database ID
            - Pass `productSku` to reference by SKU
            """
    )
    public ResponseEntity<SaleOrderResponse> createOrder(
            @Valid @RequestBody SaleOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saleOrderService.createOrder(request));
    }

    // ==================== STATUS TRANSITIONS ====================

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(
            summary = "Confirm order",
            description = "Deducts stock FIFO. If a product has insufficient stock, the item is partially filled (quantityFulfilled < quantityRequested)."
    )
    public ResponseEntity<SaleOrderResponse> confirmOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.confirmOrder(id));
    }

    @PatchMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Mark order as shipped")
    public ResponseEntity<SaleOrderResponse> shipOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.shipOrder(id));
    }

    @PatchMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Mark order as delivered")
    public ResponseEntity<SaleOrderResponse> deliverOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.deliverOrder(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(
            summary = "Cancel order",
            description = "Cancels the order. If it was already confirmed or shipped, stock is returned to the latest batch of each product."
    )
    public ResponseEntity<SaleOrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.cancelOrder(id));
    }

    // ==================== READ ====================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES', 'WAREHOUSE')")
    @Operation(summary = "Get all orders (paginated)")
    public ResponseEntity<Page<SaleOrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "10")        int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC")      String direction
    ) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.fromString(direction), sortBy));
        return ResponseEntity.ok(saleOrderService.getAllOrders(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES', 'WAREHOUSE')")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<SaleOrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(saleOrderService.getOrderById(id));
    }

    @GetMapping("/invoice/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES', 'WAREHOUSE')")
    @Operation(summary = "Get order by invoice number (e.g. INV-20260310-0001)")
    public ResponseEntity<SaleOrderResponse> getOrderByInvoice(
            @PathVariable String invoiceNumber) {
        return ResponseEntity.ok(saleOrderService.getOrderByInvoiceNumber(invoiceNumber));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES', 'WAREHOUSE')")
    @Operation(summary = "Get orders by status",
            description = "Status values: DRAFT, CONFIRMED, SHIPPED, DELIVERED, CANCELLED")
    public ResponseEntity<Page<SaleOrderResponse>> getOrdersByStatus(
            @PathVariable SaleOrderStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(saleOrderService.getOrdersByStatus(status, pageable));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(summary = "Get all orders for a customer")
    public ResponseEntity<Page<SaleOrderResponse>> getOrdersByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(saleOrderService.getOrdersByCustomer(customerId, pageable));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(summary = "Get orders within a date range",
            description = "Dates in ISO format: 2026-03-01T00:00:00")
    public ResponseEntity<Page<SaleOrderResponse>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(saleOrderService.getOrdersByDateRange(start, end, pageable));
    }

    // ==================== BULK IMPORT ====================

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(
            summary = "Bulk import orders from CSV",
            description = """
            Imports multiple orders from a CSV file.

            Required columns: orderReference, productSku (or productId), quantity
            Optional columns: customerEmail, customerFirstName, customerLastName,
                              customerPhone, customerCity, customerCountry,
                              itemNotes, orderNotes

            Grouping: rows with the same orderReference + customerEmail become ONE order.

            Example CSV:
            orderReference,customerEmail,customerFirstName,customerLastName,productSku,quantity,orderNotes
            ORD-001,john@email.com,John,Doe,PHONE-BLK,2,urgent
            ORD-001,john@email.com,John,Doe,CASE-001,3,urgent
            ORD-002,sara@email.com,Sara,Smith,LAPTOP-PRO,1,
            ORD-002,sara@email.com,Sara,Smith,MOUSE-USB,5,
            """
    )
    public ResponseEntity<SaleImportResponse> importFromCSV(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            throw new RuntimeException("File must be a .csv file");
        }
        return ResponseEntity.ok(saleImportService.importFromCSV(file));
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SALES')")
    @Operation(
            summary = "Bulk import orders from Excel (.xlsx)",
            description = """
            Same as CSV import but accepts an Excel file (.xlsx).
            First sheet is used. First row must be headers.

            Column names (case-insensitive, spaces ignored):
            orderReference, customerEmail, customerFirstName, customerLastName,
            customerPhone, customerCity, customerCountry,
            productSku (or productId), quantity, itemNotes, orderNotes
            """
    )
    public ResponseEntity<SaleImportResponse> importFromExcel(
            @RequestParam("file") MultipartFile file) throws IOException {

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new RuntimeException("File must be a .xlsx Excel file");
        }
        return ResponseEntity.ok(saleImportService.importFromExcel(file));
    }

    // ==================== DELETE ====================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete order (DRAFT only)")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        saleOrderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}