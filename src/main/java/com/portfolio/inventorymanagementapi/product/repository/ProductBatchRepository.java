package com.portfolio.inventorymanagementapi.product.repository;



import com.portfolio.inventorymanagementapi.product.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    List<ProductBatch> findByProductId(Long productId);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.product.id = :productId AND pb.remainingQuantity > 0 ORDER BY pb.createdAt ASC")
    List<ProductBatch> findAvailableBatchesByProductId(@Param("productId") Long productId);

    List<ProductBatch> findByPurchaseOrderId(Long purchaseOrderId);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.expiryDate < CURRENT_TIMESTAMP AND pb.remainingQuantity > 0")
    List<ProductBatch> findExpiredBatches();

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.expiryDate BETWEEN CURRENT_TIMESTAMP AND :date AND pb.remainingQuantity > 0")
    List<ProductBatch> findBatchesExpiringBefore(@Param("date") java.time.LocalDateTime date);
}
