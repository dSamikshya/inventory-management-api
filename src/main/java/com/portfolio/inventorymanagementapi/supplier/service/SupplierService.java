package com.portfolio.inventorymanagementapi.supplier.service;

import com.portfolio.inventorymanagementapi.supplier.dto.SupplierRequest;
import com.portfolio.inventorymanagementapi.supplier.dto.SupplierResponse;
import com.portfolio.inventorymanagementapi.supplier.entity.Supplier;
import com.portfolio.inventorymanagementapi.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;

    // ==================== CRUD ====================

    public SupplierResponse createSupplier(SupplierRequest request) {
        log.info("Creating supplier: {}", request.getName());

        if (supplierRepository.existsByName(request.getName())) {
            throw new RuntimeException("Supplier with name '" + request.getName() + "' already exists");
        }

        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .description(request.getDescription())
                .contactPerson(request.getContactPerson())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .website(request.getWebsite())
                .active(true)
                .build();

        return mapToResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = findById(id);
        return mapToResponse(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierByName(String name) {
        Supplier supplier = supplierRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Supplier not found with name: " + name));
        return mapToResponse(supplier);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(Pageable pageable) {
        return supplierRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getActiveSuppliers(Pageable pageable) {
        return supplierRepository.findByActiveTrue(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> searchSuppliers(String searchTerm, Pageable pageable) {
        return supplierRepository.searchSuppliers(searchTerm, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliersList() {
        return supplierRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        log.info("Updating supplier with id: {}", id);

        Supplier supplier = findById(id);

        if (!supplier.getName().equals(request.getName()) &&
                supplierRepository.existsByName(request.getName())) {
            throw new RuntimeException("Supplier with name '" + request.getName() + "' already exists");
        }

        supplier.setName(request.getName());
        supplier.setDescription(request.getDescription());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setWebsite(request.getWebsite());

        return mapToResponse(supplierRepository.save(supplier));
    }

    public SupplierResponse deactivateSupplier(Long id) {
        log.info("Deactivating supplier with id: {}", id);
        Supplier supplier = findById(id);
        supplier.setActive(false);
        return mapToResponse(supplierRepository.save(supplier));
    }

    public SupplierResponse activateSupplier(Long id) {
        log.info("Activating supplier with id: {}", id);
        Supplier supplier = findById(id);
        supplier.setActive(true);
        return mapToResponse(supplierRepository.save(supplier));
    }

    public void deleteSupplier(Long id) {
        log.info("Deleting supplier with id: {}", id);
        Supplier supplier = findById(id);

        boolean hasBatches = supplier.getBatches() != null && !supplier.getBatches().isEmpty();
        if (hasBatches) {
            throw new RuntimeException(
                    "Cannot delete supplier with existing batch records. Deactivate instead. " +
                            "Batch count: " + supplier.getBatches().size());
        }

        supplierRepository.delete(supplier);
        log.info("Supplier deleted successfully");
    }

    // ==================== INTERNAL ====================

    /**
     * Find or create supplier by name — used by batch import services.
     */
    public Supplier findOrCreateSupplier(String name) {
        if (name == null || name.isBlank()) return null;

        return supplierRepository.findByName(name)
                .orElseGet(() -> {
                    log.info("Auto-creating supplier: {}", name);
                    Supplier s = Supplier.builder()
                            .name(name)
                            .description("Auto-created from batch import")
                            .active(true)
                            .build();
                    return supplierRepository.save(s);
                });
    }

    private Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }

    // ==================== MAPPING ====================

    private SupplierResponse mapToResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .description(supplier.getDescription())
                .contactPerson(supplier.getContactPerson())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .address(supplier.getAddress())
                .website(supplier.getWebsite())
                .active(supplier.isActive())
                .batchCount(supplier.getBatches() != null ? supplier.getBatches().size() : 0)
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}