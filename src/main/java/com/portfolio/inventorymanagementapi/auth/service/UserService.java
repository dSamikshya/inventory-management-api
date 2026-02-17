package com.portfolio.inventorymanagementapi.auth.service;

import com.portfolio.inventorymanagementapi.auth.dto.UserResponse;
import com.portfolio.inventorymanagementapi.auth.entity.Role;
import com.portfolio.inventorymanagementapi.auth.entity.User;
import com.portfolio.inventorymanagementapi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user with email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching all users");
        return userRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByRole(Role role, Pageable pageable) {
        log.debug("Fetching users by role: {}", role);
        return userRepository.findByRole(role, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getActiveUsers(Boolean active, Pageable pageable) {
        log.debug("Fetching active users: {}", active);
        return userRepository.findByActive(active, pageable)
                .map(this::mapToResponse);
    }

    public UserResponse updateUser(Long id, User userDetails) {
        log.info("Updating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setPhoneNumber(userDetails.getPhoneNumber());

        User updated = userRepository.save(user);
        log.info("User updated successfully");

        return mapToResponse(updated);
    }

    public void deactivateUser(Long id) {
        log.info("Deactivating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setActive(false);
        userRepository.save(user);

        log.info("User deactivated successfully");
    }

    public void activateUser(Long id) {
        log.info("Activating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setActive(true);
        userRepository.save(user);

        log.info("User activated successfully");
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}