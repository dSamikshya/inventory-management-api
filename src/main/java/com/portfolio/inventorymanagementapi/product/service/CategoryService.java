package com.portfolio.inventorymanagementapi.product.service;



import com.portfolio.inventorymanagementapi.product.dto.CategoryRequest;
import com.portfolio.inventorymanagementapi.product.dto.CategoryResponse;
import com.portfolio.inventorymanagementapi.product.entity.Category;
import com.portfolio.inventorymanagementapi.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        log.debug("Fetching category with id: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryByName(String name) {
        log.debug("Fetching category with name: {}", name);
        Category category = categoryRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Category not found with name: " + name));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> getAllCategories(Pageable pageable) {
        log.debug("Fetching all categories with pagination");
        return categoryRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoriesList() {
        log.debug("Fetching all categories as list");
        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        log.info("Creating new category: {}", request.getName());

        if (categoryRepository.existsByName(request.getName())) {
            throw new RuntimeException("Category with name " + request.getName() + " already exists");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Category created successfully with id: {}", saved.getId());

        return mapToResponse(saved);
    }

    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        log.info("Updating category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        // Check if name is being changed and if it already exists
        if (!category.getName().equals(request.getName()) &&
                categoryRepository.existsByName(request.getName())) {
            throw new RuntimeException("Category with name " + request.getName() + " already exists");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        Category updated = categoryRepository.save(category);
        log.info("Category updated successfully");

        return mapToResponse(updated);
    }

    public void deleteCategory(Long id) {
        log.info("Deleting category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        // Check if category has products
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            throw new RuntimeException("Cannot delete category with existing products. Product count: " + category.getProducts().size());
        }

        categoryRepository.delete(category);
        log.info("Category deleted successfully");
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .productCount(category.getProducts() != null ? category.getProducts().size() : 0)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public List<CategoryResponse> createCategoriesBulk(List<CategoryRequest> requests) {
        log.info("Creating {} categories in bulk", requests.size());

        List<CategoryResponse> createdCategories = new ArrayList<>();

        for (CategoryRequest request : requests) {
            try {
                CategoryResponse created = createCategory(request);
                createdCategories.add(created);
            } catch (Exception e) {
                log.error("Error creating category {}: {}", request.getName(), e.getMessage());
                // Skip and continue
            }
        }

        log.info("Bulk create completed. Created: {}/{}", createdCategories.size(), requests.size());
        return createdCategories;
    }

    public List<CategoryResponse> updateCategoriesBulk(List<CategoryRequest> requests) {
        log.info("Updating {} categories in bulk", requests.size());

        List<CategoryResponse> updatedCategories = new ArrayList<>();

        for (CategoryRequest request : requests) {
            try {
                // Find by name
                Category category = categoryRepository.findByName(request.getName())
                        .orElseThrow(() -> new RuntimeException("Category not found: " + request.getName()));

                CategoryResponse updated = updateCategory(category.getId(), request);
                updatedCategories.add(updated);
            } catch (Exception e) {
                log.error("Error updating category {}: {}", request.getName(), e.getMessage());
            }
        }

        log.info("Bulk update completed. Updated: {}/{}", updatedCategories.size(), requests.size());
        return updatedCategories;
    }
}
