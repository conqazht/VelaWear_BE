package vn.conganh.commercial.feature.productvariant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id and v.deletedAt is null")
    Optional<ProductVariant> findWithLockByIdAndDeletedAtIsNull(@Param("id") Long id);

    Page<ProductVariant> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySku(String sku);

    List<ProductVariant> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id in :ids and v.deletedAt is null order by v.id asc")
    List<ProductVariant> findAllByIdsWithLock(@Param("ids") List<Long> ids);

    @Modifying
    @Query("""
            UPDATE ProductVariant pv
            SET pv.stockQuantity = pv.stockQuantity - :quantity
            WHERE pv.id = :variantId
              AND pv.stockQuantity >= :quantity
              AND pv.status = 'ACTIVE'
              AND pv.deletedAt IS NULL
            """)
    int decrementStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    @Modifying
    @Query("""
            UPDATE ProductVariant pv
            SET pv.stockQuantity = pv.stockQuantity + :quantity
            WHERE pv.id = :variantId
            """)
    int restoreStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(List<Long> productIds);

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(Long productId);

}
