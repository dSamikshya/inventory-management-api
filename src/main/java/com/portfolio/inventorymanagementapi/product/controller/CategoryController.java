package com.portfolio.inventorymanagementapi.product.controller;



import com.portfolio.inventorymanagementapi.product.dto.CategoryRequest;
import com.portfolio.inventorymanagementapi.product.dto.CategoryResponse;
import com.portfolio.inventorymanagementapi.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories", description = "Retrieve paginated list of all categories")
    public ResponseEntity<Page<CategoryResponse>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction
    ) {
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(categoryService.getAllCategories(pageable));
    }

    @GetMapping("/list")
    @Operation(summary = "Get all categories as list", description = "Retrieve all categories without pagination")
    public ResponseEntity<List<CategoryResponse>> getAllCategoriesList() {
        return ResponseEntity.ok(categoryService.getAllCategoriesList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Retrieve a category by its ID")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get category by name", description = "Retrieve a category by its name")
    public ResponseEntity<CategoryResponse> getCategoryByName(@PathVariable String name) {
        return ResponseEntity.ok(categoryService.getCategoryByName(name));
    }

    @PostMapping
    @Operation(summary = "Create new category", description = "Create a new product category")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Update an existing category")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category", description = "Delete a category (only if no products exist)")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create multiple categories")
    public ResponseEntity<List<CategoryResponse>> createCategoriesBulk(
            @Valid @RequestBody List<@Valid CategoryRequest> requests
    ) {
        List<CategoryResponse> created = categoryService.createCategoriesBulk(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/bulk")
    @Operation(summary = "Update multiple categories")
    public ResponseEntity<List<CategoryResponse>> updateCategoriesBulk(
            @Valid @RequestBody List<@Valid CategoryRequest> requests
    ) {
        List<CategoryResponse> updated = categoryService.updateCategoriesBulk(requests);
        return ResponseEntity.ok(updated);
    }
}
