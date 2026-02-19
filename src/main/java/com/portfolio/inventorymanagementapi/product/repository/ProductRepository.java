package com.portfolio.inventorymanagementapi.product.repository;

import com.portfolio.inventorymanagementapi.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    boolean existsByName(String name);

    boolean existsByBarcode(String barcode);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Low-stock: native query because totalQuantity is a @Formula field
     * and is not addressable in JPQL.
     */
    @Query(value = """
            SELECT p.* FROM products p
            WHERE (
                SELECT COALESCE(SUM(pb.remaining_quantity), 0)
                FROM product_batches pb
                WHERE pb.product_id = p.id
            ) <= p.reorder_threshold
            AND p.active = true
            ORDER BY (
                SELECT COALESCE(SUM(pb.remaining_quantity), 0)
                FROM product_batches pb
                WHERE pb.product_id = p.id
            ) ASC
            """,
            nativeQuery = true)
    List<Product> findLowStockProducts();

    @Query(value = """
            SELECT COUNT(*) FROM products p
            WHERE (
                SELECT COALESCE(SUM(pb.remaining_quantity), 0)
                FROM product_batches pb
                WHERE pb.product_id = p.id
            ) <= p.reorder_threshold
            AND p.active = true
            """,
            nativeQuery = true)
    Long countLowStockProducts();

    @Query("SELECT p FROM Product p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.sku)  LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> searchProducts(@Param("search") String search, Pageable pageable);
}