package com.portfolio.inventorymanagementapi.customer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_email", columnList = "email"),
        @Index(name = "idx_customer_status", columnList = "status"),
        @Index(name = "idx_customer_name", columnList = "first_name, last_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(length = 100)
    private String country;

    @Column(precision = 12, scale = 2)
    private BigDecimal creditLimit; // Maximum amount they can owe

    @Column(precision = 12, scale = 2)
    private BigDecimal currentBalance; // Amount they currently owe

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Helper method to get full name
    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Helper method to check if customer can purchase
    @Transient
    public boolean canPurchase(BigDecimal amount) {
        if (status != CustomerStatus.ACTIVE) {
            return false;
        }
        if (creditLimit == null) {
            return true; // No credit limit means unlimited
        }
        BigDecimal balance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        return balance.add(amount).compareTo(creditLimit) <= 0;
    }

    // Helper method to get available credit
    @Transient
    public BigDecimal getAvailableCredit() {
        if (creditLimit == null) {
            return null; // No credit limit
        }
        BigDecimal balance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        return creditLimit.subtract(balance);
    }
}