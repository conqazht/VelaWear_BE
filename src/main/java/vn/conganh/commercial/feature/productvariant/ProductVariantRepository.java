package vn.conganh.commercial.feature.productvariant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductVariant> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySku(String sku);

    List<ProductVariant> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

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
              AND pv.deletedAt IS NULL
            """)
    int restoreStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(List<Long> productIds);

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(Long productId);

    @Query("""
            select v.product.id as productId,
                   v.price as price,
                   case
                       when v.salePrice is not null and v.salePrice > 0 and v.salePrice < v.price
                       then v.salePrice
                       else null
                   end as salePrice
            from ProductVariant v
            where v.deletedAt is null
              and v.product.id in :productIds
              and not exists (
                  select 1
                  from ProductVariant other
                  where other.deletedAt is null
                    and other.product.id = v.product.id
                    and (
                        case
                            when other.salePrice is not null and other.salePrice > 0 and other.salePrice < other.price
                            then other.salePrice
                            else other.price
                        end
                        <
                        case
                            when v.salePrice is not null and v.salePrice > 0 and v.salePrice < v.price
                            then v.salePrice
                            else v.price
                        end
                        or (
                            case
                                when other.salePrice is not null and other.salePrice > 0 and other.salePrice < other.price
                                then other.salePrice
                                else other.price
                            end
                            =
                            case
                                when v.salePrice is not null and v.salePrice > 0 and v.salePrice < v.price
                                then v.salePrice
                                else v.price
                            end
                            and other.id < v.id
                        )
                    )
              )
            """)
    List<RepresentativePrice> findRepresentativePricesByProductIds(@Param("productIds") List<Long> productIds);

    interface RepresentativePrice {
        Long getProductId();

        BigDecimal getPrice();

        BigDecimal getSalePrice();
    }
}
