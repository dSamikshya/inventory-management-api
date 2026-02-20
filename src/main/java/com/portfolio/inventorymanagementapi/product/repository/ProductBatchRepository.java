package com.portfolio.inventorymanagementapi.product.repository;

import com.portfolio.inventorymanagementapi.product.entity.ProductBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    List<ProductBatch> findByProductId(Long productId);

    Page<ProductBatch> findByProductId(Long productId, Pageable pageable);

    /** FIFO: oldest first, only non-depleted batches. */
    @Query("SELECT pb FROM ProductBatch pb " +
            "WHERE pb.product.id = :productId AND pb.remainingQuantity > 0 " +
            "ORDER BY pb.createdAt ASC")
    List<ProductBatch> findAvailableBatchesByProductId(@Param("productId") Long productId);

    List<ProductBatch> findByPurchaseOrderId(String purchaseOrderId);

    List<ProductBatch> findBySupplierId(Long supplierId);

    @Query("SELECT pb FROM ProductBatch pb " +
            "WHERE pb.expiryDate < CURRENT_TIMESTAMP AND pb.remainingQuantity > 0")
    List<ProductBatch> findExpiredBatches();

    @Query("SELECT pb FROM ProductBatch pb " +
            "WHERE pb.expiryDate BETWEEN CURRENT_TIMESTAMP AND :date " +
            "AND pb.remainingQuantity > 0 " +
            "ORDER BY pb.expiryDate ASC")
    List<ProductBatch> findBatchesExpiringBefore(@Param("date") LocalDateTime date);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.remainingQuantity = 0")
    List<ProductBatch> findDepletedBatches();
}