package com.portfolio.inventorymanagementapi.sales.service;

import com.portfolio.inventorymanagementapi.customer.entity.Customer;
import com.portfolio.inventorymanagementapi.customer.entity.CustomerStatus;
import com.portfolio.inventorymanagementapi.customer.repository.CustomerRepository;
import com.portfolio.inventorymanagementapi.product.entity.Product;
import com.portfolio.inventorymanagementapi.product.entity.ProductBatch;
import com.portfolio.inventorymanagementapi.product.repository.ProductBatchRepository;
import com.portfolio.inventorymanagementapi.product.repository.ProductRepository;
import com.portfolio.inventorymanagementapi.sales.dto.*;
import com.portfolio.inventorymanagementapi.sales.entity.SaleOrder;
import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderItem;
import com.portfolio.inventorymanagementapi.sales.entity.SaleOrderStatus;
import com.portfolio.inventorymanagementapi.sales.repository.SaleOrderItemRepository;
import com.portfolio.inventorymanagementapi.sales.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleOrderService {

    private final SaleOrderRepository      saleOrderRepository;
    private final SaleOrderItemRepository  saleOrderItemRepository;
    private final ProductRepository        productRepository;
    private final ProductBatchRepository   productBatchRepository;
    private final CustomerRepository       customerRepository;

    // ==================== CREATE ====================

    /**
     * Create a new order in DRAFT status.
     *
     * Customer resolution:
     *   1. customerId provided          → find by ID (throws if not found)
     *   2. customerEmail/name provided  → find by email or auto-create customer
     *   3. nothing provided             → walk-in (order saved without customer)
     *
     * Stock is NOT deducted here — happens on confirmOrder().
     */
    @Transactional
    public SaleOrderResponse createOrder(SaleOrderRequest request) {
        // Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Order must have at least one item");
        }
        for (SaleOrderItemRequest item : request.getItems()) {
            if (!item.hasProductIdentifier()) {
                throw new RuntimeException(
                        "Each item must have either productId or productSku");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException("Each item quantity must be positive");
            }
        }

        log.info("Creating sale order with {} items", request.getItems().size());

        // Resolve customer
        Customer customer = resolveOrCreateCustomer(request);

        // Build and persist order shell first (need ID for items)
        SaleOrder order = SaleOrder.builder()
                .invoiceNumber(generateInvoiceNumber())
                .customer(customer)
                .status(SaleOrderStatus.DRAFT)
                .notes(request.getNotes())
                .build();

        order = saleOrderRepository.saveAndFlush(order);

        // Build items — resolve product by ID or SKU
        for (SaleOrderItemRequest req : request.getItems()) {
            Product product = resolveProduct(req);

            SaleOrderItem item = SaleOrderItem.builder()
                    .saleOrder(order)
                    .product(product)
                    .quantityRequested(req.getQuantity())
                    .quantityFulfilled(0)
                    .unitSellingPrice(product.getSellingPrice())
                    .unitCostPrice(BigDecimal.ZERO)
                    .totalPrice(BigDecimal.ZERO)
                    .totalCost(BigDecimal.ZERO)
                    .profit(BigDecimal.ZERO)
                    .partialFill(false)
                    .notes(req.getNotes())
                    .build();

            // CRITICAL: mutate the existing collection — never reassign with setItems()
            // because the collection is Hibernate-managed (orphanRemoval = true)
            order.getItems().add(saleOrderItemRepository.save(item));
        }

        recalculateTotals(order);
        order = saleOrderRepository.save(order);

        log.info("Sale order created: {} (DRAFT) — customer: {}",
                order.getInvoiceNumber(),
                customer != null ? customer.getEmail() : "walk-in");

        return mapToResponse(order);
    }

    // ==================== STATUS TRANSITIONS ====================

    /**
     * Confirm order — deducts stock FIFO from batches.
     * Partial fill: if stock runs out, fulfills what's available.
     */
    @Transactional
    public SaleOrderResponse confirmOrder(Long orderId) {
        SaleOrder order = findOrderById(orderId);

        if (order.getStatus() != SaleOrderStatus.DRAFT) {
            throw new RuntimeException(
                    "Only DRAFT orders can be confirmed. Current status: " + order.getStatus());
        }

        log.info("Confirming order: {}", order.getInvoiceNumber());

        for (SaleOrderItem item : order.getItems()) {
            deductStockFIFO(item);
        }

        recalculateTotals(order);
        order.setStatus(SaleOrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        order = saleOrderRepository.save(order);

        log.info("Order confirmed: {} | Revenue: {} | Profit: {}",
                order.getInvoiceNumber(), order.getTotalAmount(), order.getTotalProfit());
        return mapToResponse(order);
    }

    @Transactional
    public SaleOrderResponse shipOrder(Long orderId) {
        SaleOrder order = findOrderById(orderId);
        if (order.getStatus() != SaleOrderStatus.CONFIRMED) {
            throw new RuntimeException(
                    "Only CONFIRMED orders can be shipped. Current: " + order.getStatus());
        }
        order.setStatus(SaleOrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        log.info("Order shipped: {}", order.getInvoiceNumber());
        return mapToResponse(saleOrderRepository.save(order));
    }

    @Transactional
    public SaleOrderResponse deliverOrder(Long orderId) {
        SaleOrder order = findOrderById(orderId);
        if (order.getStatus() != SaleOrderStatus.SHIPPED) {
            throw new RuntimeException(
                    "Only SHIPPED orders can be delivered. Current: " + order.getStatus());
        }
        order.setStatus(SaleOrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        log.info("Order delivered: {}", order.getInvoiceNumber());
        return mapToResponse(saleOrderRepository.save(order));
    }

    /**
     * Cancel order — returns stock to batches if already confirmed/shipped.
     */
    @Transactional
    public SaleOrderResponse cancelOrder(Long orderId) {
        SaleOrder order = findOrderById(orderId);

        if (order.getStatus() == SaleOrderStatus.DELIVERED
                || order.getStatus() == SaleOrderStatus.CANCELLED) {
            throw new RuntimeException("Cannot cancel a " + order.getStatus() + " order");
        }

        if (order.getStatus() == SaleOrderStatus.CONFIRMED
                || order.getStatus() == SaleOrderStatus.SHIPPED) {
            log.info("Returning stock for cancelled order: {}", order.getInvoiceNumber());
            returnStockToLatestBatch(order);
        }

        order.setStatus(SaleOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        log.info("Order cancelled: {}", order.getInvoiceNumber());
        return mapToResponse(saleOrderRepository.save(order));
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public SaleOrderResponse getOrderById(Long id) {
        return mapToResponse(findOrderById(id));
    }

    @Transactional(readOnly = true)
    public SaleOrderResponse getOrderByInvoiceNumber(String invoiceNumber) {
        return mapToResponse(
                saleOrderRepository.findByInvoiceNumber(invoiceNumber)
                        .orElseThrow(() -> new RuntimeException(
                                "Order not found with invoice: " + invoiceNumber)));
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderResponse> getAllOrders(Pageable pageable) {
        return saleOrderRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderResponse> getOrdersByStatus(SaleOrderStatus status, Pageable pageable) {
        return saleOrderRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderResponse> getOrdersByCustomer(Long customerId, Pageable pageable) {
        return saleOrderRepository.findByCustomerId(customerId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SaleOrderResponse> getOrdersByDateRange(
            LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return saleOrderRepository.findByDateRange(start, end, pageable).map(this::mapToResponse);
    }

    // ==================== DELETE ====================

    @Transactional
    public void deleteOrder(Long orderId) {
        SaleOrder order = findOrderById(orderId);
        if (order.getStatus() != SaleOrderStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT orders can be deleted");
        }
        saleOrderRepository.delete(order);
        log.info("Draft order deleted: {}", order.getInvoiceNumber());
    }

    // ==================== FIFO STOCK DEDUCTION ====================

    /**
     * Deducts from batches oldest-first (FIFO).
     * Sets quantityFulfilled, unitCostPrice, totalPrice, totalCost, profit on the item.
     */
    private void deductStockFIFO(SaleOrderItem item) {
        Product product = item.getProduct();
        int needed = item.getQuantityRequested();

        // Batches ordered by createdAt ASC (oldest first = FIFO)
        List<ProductBatch> batches =
                productBatchRepository.findAvailableBatchesByProductId(product.getId());

        if (batches.isEmpty()) {
            log.warn("No stock for product: {}", product.getSku());
            item.setQuantityFulfilled(0);
            item.setUnitCostPrice(BigDecimal.ZERO);
            item.setTotalPrice(BigDecimal.ZERO);
            item.setTotalCost(BigDecimal.ZERO);
            item.setProfit(BigDecimal.ZERO);
            item.setPartialFill(true);
            return;
        }

        int fulfilled = 0;
        BigDecimal totalCostForItem = BigDecimal.ZERO;

        for (ProductBatch batch : batches) {
            if (needed <= 0) break;

            int available = batch.getRemainingQuantity();
            int toDeduct  = Math.min(needed, available);

            batch.setRemainingQuantity(available - toDeduct);
            productBatchRepository.save(batch);

            totalCostForItem = totalCostForItem.add(
                    batch.getCostPrice().multiply(BigDecimal.valueOf(toDeduct)));

            fulfilled += toDeduct;
            needed    -= toDeduct;

            log.debug("FIFO: deducted {} from batch id={} (remaining: {})",
                    toDeduct, batch.getId(), batch.getRemainingQuantity());
        }

        // Weighted average cost across all batches used
        BigDecimal unitCost = fulfilled > 0
                ? totalCostForItem.divide(BigDecimal.valueOf(fulfilled), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalPrice = item.getUnitSellingPrice().multiply(BigDecimal.valueOf(fulfilled));
        BigDecimal totalCost  = unitCost.multiply(BigDecimal.valueOf(fulfilled));

        item.setQuantityFulfilled(fulfilled);
        item.setUnitCostPrice(unitCost);
        item.setTotalPrice(totalPrice);
        item.setTotalCost(totalCost);
        item.setProfit(totalPrice.subtract(totalCost));
        item.setPartialFill(fulfilled < item.getQuantityRequested());

        if (item.isPartialFill()) {
            log.warn("Partial fill: {} requested={} fulfilled={}",
                    product.getSku(), item.getQuantityRequested(), fulfilled);
        }
    }

    /**
     * Returns fulfilled stock back to the most recent batch of each product.
     * Used on cancellation after stock was already deducted.
     */
    private void returnStockToLatestBatch(SaleOrder order) {
        for (SaleOrderItem item : order.getItems()) {
            if (item.getQuantityFulfilled() <= 0) continue;

            List<ProductBatch> batches =
                    productBatchRepository.findByProductId(item.getProduct().getId());

            if (!batches.isEmpty()) {
                ProductBatch latest = batches.get(batches.size() - 1);
                latest.setRemainingQuantity(
                        latest.getRemainingQuantity() + item.getQuantityFulfilled());
                productBatchRepository.save(latest);

                log.info("Returned {} × {} to batch id={}",
                        item.getQuantityFulfilled(), item.getProduct().getSku(), latest.getId());
            }
        }
    }

    // ==================== HELPERS ====================

    /**
     * Resolve product by ID (preferred) or SKU.
     */
    private Product resolveProduct(SaleOrderItemRequest req) {
        if (req.getProductId() != null) {
            return productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found with id: " + req.getProductId()));
        }
        return productRepository.findBySku(req.getProductSku())
                .orElseThrow(() -> new RuntimeException(
                        "Product not found with SKU: " + req.getProductSku()));
    }

    /**
     * Customer resolution logic:
     *   1. customerId → find by ID (required to exist)
     *   2. customerEmail → find by email; if not found, create new customer
     *   3. nothing → walk-in (returns null)
     */
    private Customer resolveOrCreateCustomer(SaleOrderRequest request) {
        // Option A: existing customer by ID
        if (request.getCustomerId() != null) {
            return customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException(
                            "Customer not found with id: " + request.getCustomerId()));
        }

        // Option B: email or name provided → find or create
        if (request.hasCustomerDetails()) {
            if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
                // Try to find by email first
                return customerRepository.findByEmail(request.getCustomerEmail())
                        .orElseGet(() -> {
                            log.info("Auto-creating customer: {}", request.getCustomerEmail());
                            Customer c = new Customer();
                            c.setFirstName(request.getCustomerFirstName() != null
                                    ? request.getCustomerFirstName() : "Unknown");
                            c.setLastName(request.getCustomerLastName() != null
                                    ? request.getCustomerLastName() : "Unknown");
                            c.setEmail(request.getCustomerEmail());
                            c.setPhoneNumber(request.getCustomerPhone());
                            c.setCity(request.getCustomerCity());
                            c.setCountry(request.getCustomerCountry());
                            c.setStatus(CustomerStatus.ACTIVE);
                            c.setCurrentBalance(BigDecimal.ZERO);
                            return customerRepository.save(c);
                        });
            }
            // Name provided but no email — create without email (allow null)
            log.info("Creating walk-in customer with name: {} {}",
                    request.getCustomerFirstName(), request.getCustomerLastName());
            Customer c = new Customer();
            c.setFirstName(request.getCustomerFirstName());
            c.setLastName(request.getCustomerLastName() != null
                    ? request.getCustomerLastName() : "");
            c.setPhoneNumber(request.getCustomerPhone());
            c.setCity(request.getCustomerCity());
            c.setCountry(request.getCustomerCountry());
            c.setStatus(CustomerStatus.ACTIVE);
            c.setCurrentBalance(BigDecimal.ZERO);
            return customerRepository.save(c);
        }

        // Option C: walk-in
        return null;
    }

    private void recalculateTotals(SaleOrder order) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost   = BigDecimal.ZERO;
        boolean    partial     = false;

        for (SaleOrderItem item : order.getItems()) {
            totalAmount = totalAmount.add(item.getTotalPrice());
            totalCost   = totalCost.add(item.getTotalCost());
            if (item.isPartialFill()) partial = true;
        }

        order.setTotalAmount(totalAmount);
        order.setTotalCost(totalCost);
        order.setTotalProfit(totalAmount.subtract(totalCost));
        order.setPartialFill(partial);
    }

    private String generateInvoiceNumber() {
        String prefix = "INV-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<String> existing = saleOrderRepository.findLatestInvoiceNumbers(prefix + "%");
        int next = 1;
        if (!existing.isEmpty()) {
            String[] parts = existing.get(0).split("-");
            next = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return String.format("%s-%04d", prefix, next);
    }

    private SaleOrder findOrderById(Long id) {
        return saleOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale order not found with id: " + id));
    }

    // ==================== MAPPING ====================

    public SaleOrderResponse mapToResponse(SaleOrder order) {
        List<SaleOrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(this::mapItemToResponse).collect(Collectors.toList())
                : List.of();

        return SaleOrderResponse.builder()
                .id(order.getId())
                .invoiceNumber(order.getInvoiceNumber())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null
                        ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName()
                        : "Walk-in")
                .status(order.getStatus())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .totalCost(order.getTotalCost())
                .totalProfit(order.getTotalProfit())
                .partialFill(order.isPartialFill())
                .notes(order.getNotes())
                .confirmedAt(order.getConfirmedAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private SaleOrderItemResponse mapItemToResponse(SaleOrderItem item) {
        return SaleOrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productSku(item.getProduct().getSku())
                .quantityRequested(item.getQuantityRequested())
                .quantityFulfilled(item.getQuantityFulfilled())
                .unitSellingPrice(item.getUnitSellingPrice())
                .unitCostPrice(item.getUnitCostPrice())
                .totalPrice(item.getTotalPrice())
                .totalCost(item.getTotalCost())
                .profit(item.getProfit())
                .partialFill(item.isPartialFill())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .build();
    }
}