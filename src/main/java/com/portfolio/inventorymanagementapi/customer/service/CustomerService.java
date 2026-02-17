package com.portfolio.inventorymanagementapi.customer.service;

import com.portfolio.inventorymanagementapi.customer.dto.CustomerRequest;
import com.portfolio.inventorymanagementapi.customer.dto.CustomerResponse;
import com.portfolio.inventorymanagementapi.customer.entity.Customer;
import com.portfolio.inventorymanagementapi.customer.entity.CustomerStatus;
import com.portfolio.inventorymanagementapi.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    // Create customer
    @Transactional
    @CacheEvict(value = "customers", allEntries = true)
    public CustomerResponse createCustomer(CustomerRequest request) {
        // Check if email already exists
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Customer with email " + request.getEmail() + " already exists");
        }

        Customer customer = mapToEntity(request);
        customer = customerRepository.save(customer);
        return mapToResponse(customer);
    }

    // Bulk create customers
    @Transactional
    @CacheEvict(value = "customers", allEntries = true)
    public List<CustomerResponse> createCustomersBulk(List<CustomerRequest> requests) {
        List<CustomerResponse> responses = new ArrayList<>();

        for (CustomerRequest request : requests) {
            try {
                if (!customerRepository.existsByEmail(request.getEmail())) {
                    Customer customer = mapToEntity(request);
                    customer = customerRepository.save(customer);
                    responses.add(mapToResponse(customer));
                }
            } catch (Exception e) {
                // Skip invalid entries
                System.err.println("Failed to create customer: " + e.getMessage());
            }
        }

        return responses;
    }

    // Get customer by ID
    @Cacheable(value = "customers", key = "#id")
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        return mapToResponse(customer);
    }

    // Get customer by email
    public CustomerResponse getCustomerByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Customer not found with email: " + email));
        return mapToResponse(customer);
    }

    // Get all customers (paginated)
    public Page<CustomerResponse> getAllCustomers(int page, int size, String sortBy, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return customerRepository.findAll(pageable).map(this::mapToResponse);
    }

    // Get all customers (list - no pagination)
    public List<CustomerResponse> getAllCustomersList() {
        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Get customers by status
    public Page<CustomerResponse> getCustomersByStatus(CustomerStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return customerRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    // Search customers
    public Page<CustomerResponse> searchCustomers(String searchTerm, CustomerStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return customerRepository.search(searchTerm, status, pageable).map(this::mapToResponse);
    }

    // Get customers by city
    public Page<CustomerResponse> getCustomersByCity(String city, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return customerRepository.findByCity(city, pageable).map(this::mapToResponse);
    }

    // Update customer
    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));

        // Check email uniqueness if changed
        if (!customer.getEmail().equals(request.getEmail()) &&
                customerRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use: " + request.getEmail());
        }

        updateEntityFromRequest(customer, request);
        customer = customerRepository.save(customer);
        return mapToResponse(customer);
    }

    // Bulk update customers
    @Transactional
    @CacheEvict(value = "customers", allEntries = true)
    public List<CustomerResponse> updateCustomersBulk(List<CustomerRequest> requests) {
        List<CustomerResponse> responses = new ArrayList<>();

        for (CustomerRequest request : requests) {
            try {
                customerRepository.findByEmail(request.getEmail()).ifPresent(customer -> {
                    updateEntityFromRequest(customer, request);
                    Customer updated = customerRepository.save(customer);
                    responses.add(mapToResponse(updated));
                });
            } catch (Exception e) {
                System.err.println("Failed to update customer: " + e.getMessage());
            }
        }

        return responses;
    }

    // Delete customer
    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new RuntimeException("Customer not found with id: " + id);
        }
        customerRepository.deleteById(id);
    }

    // Update customer balance (used by Sales module)
    @Transactional
    @CacheEvict(value = "customers", key = "#customerId")
    public void updateBalance(Long customerId, BigDecimal amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));

        BigDecimal currentBalance = customer.getCurrentBalance() != null ?
                customer.getCurrentBalance() : BigDecimal.ZERO;
        customer.setCurrentBalance(currentBalance.add(amount));
        customerRepository.save(customer);
    }

    // Block customer
    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public CustomerResponse blockCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        customer.setStatus(CustomerStatus.BLOCKED);
        customer = customerRepository.save(customer);
        return mapToResponse(customer);
    }

    // Activate customer
    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public CustomerResponse activateCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        customer.setStatus(CustomerStatus.ACTIVE);
        customer = customerRepository.save(customer);
        return mapToResponse(customer);
    }

    // Get customers exceeding credit limit
    public List<CustomerResponse> getCustomersExceedingCreditLimit() {
        return customerRepository.findCustomersExceedingCreditLimit()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Get count by status
    public long getCountByStatus(CustomerStatus status) {
        return customerRepository.countByStatus(status);
    }

    // Helper: Map entity to response
    private CustomerResponse mapToResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        response.setFullName(customer.getFullName());
        response.setEmail(customer.getEmail());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setAddress(customer.getAddress());
        response.setCity(customer.getCity());
        response.setState(customer.getState());
        response.setZipCode(customer.getZipCode());
        response.setCountry(customer.getCountry());
        response.setCreditLimit(customer.getCreditLimit());
        response.setCurrentBalance(customer.getCurrentBalance());
        response.setAvailableCredit(customer.getAvailableCredit());
        response.setStatus(customer.getStatus());
        response.setNotes(customer.getNotes());
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        return response;
    }

    // Helper: Map request to entity
    private Customer mapToEntity(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());
        customer.setCity(request.getCity());
        customer.setState(request.getState());
        customer.setZipCode(request.getZipCode());
        customer.setCountry(request.getCountry());
        customer.setCreditLimit(request.getCreditLimit());
        customer.setCurrentBalance(request.getCurrentBalance() != null ?
                request.getCurrentBalance() : BigDecimal.ZERO);
        customer.setStatus(request.getStatus() != null ?
                request.getStatus() : CustomerStatus.ACTIVE);
        customer.setNotes(request.getNotes());
        return customer;
    }

    // Helper: Update entity from request
    private void updateEntityFromRequest(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());
        customer.setCity(request.getCity());
        customer.setState(request.getState());
        customer.setZipCode(request.getZipCode());
        customer.setCountry(request.getCountry());
        customer.setCreditLimit(request.getCreditLimit());
        if (request.getCurrentBalance() != null) {
            customer.setCurrentBalance(request.getCurrentBalance());
        }
        if (request.getStatus() != null) {
            customer.setStatus(request.getStatus());
        }
        customer.setNotes(request.getNotes());
    }
}
