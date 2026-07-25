package vn.conganh.commercial.feature.salecampaign;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleCampaignItemRepository extends JpaRepository<SaleCampaignItem, Long> {

    @Query("""
            select (count(i) > 0) from SaleCampaignItem i
            where i.variant.id = :variantId
              and (
                    i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.DRAFT
                    or (
                        i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
                        and i.campaign.endsAt > :now
                    )
              )
            """)
    boolean existsProtectedVariant(@Param("variantId") Long variantId, @Param("now") Instant now);

    @Query("""
            select (count(i) > 0) from SaleCampaignItem i
            where i.variant.product.id = :productId
              and (
                    i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.DRAFT
                    or (
                        i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
                        and i.campaign.endsAt > :now
                    )
              )
            """)
    boolean existsProtectedProduct(@Param("productId") Long productId, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"campaign", "variant", "variant.product"})
    @Query("""
            select i from SaleCampaignItem i
            where i.variant.id in :variantIds
              and i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
              and i.campaign.startsAt <= :now
              and i.campaign.endsAt > :now
            order by i.variant.id asc, i.campaign.type desc, i.promotionalPrice asc, i.id asc
            """)
    List<SaleCampaignItem> findActiveForVariants(
            @Param("variantIds") List<Long> variantIds,
            @Param("now") Instant now);

    @EntityGraph(attributePaths = {"campaign", "variant", "variant.product"})
    @Query("""
            select i from SaleCampaignItem i
            where i.variant.id = :variantId
              and i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
              and i.campaign.startsAt < :endsAt
              and i.campaign.endsAt > :startsAt
              and (:excludedCampaignId is null or i.campaign.id <> :excludedCampaignId)
            """)
    List<SaleCampaignItem> findOverlappingForVariant(
            @Param("variantId") Long variantId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludedCampaignId") Long excludedCampaignId);

    @EntityGraph(attributePaths = {"campaign", "variant", "variant.product"})
    @Query("""
            select i from SaleCampaignItem i
            where i.variant.id in :variantIds
              and i.campaign.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
              and i.campaign.startsAt < :endsAt
              and i.campaign.endsAt > :startsAt
              and (:excludedCampaignId is null or i.campaign.id <> :excludedCampaignId)
            """)
    List<SaleCampaignItem> findOverlappingForVariants(
            @Param("variantIds") List<Long> variantIds,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludedCampaignId") Long excludedCampaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaign", "variant"})
    @Query("select i from SaleCampaignItem i where i.id = :id")
    Optional<SaleCampaignItem> findWithLockById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update sale_campaign_items item
               set reserved_quantity = item.reserved_quantity + :quantity
             where item.id = :itemId
               and item.quota is not null
               and item.reserved_quantity + item.sold_quantity + :quantity <= item.quota
               and exists (
                    select 1
                      from sale_campaigns campaign
                     where campaign.id = item.campaign_id
                       and campaign.status = 'PUBLISHED'
                       and campaign.starts_at <= :now
                       and campaign.ends_at > :now
               )
            """, nativeQuery = true)
    int reserveQuota(@Param("itemId") Long itemId, @Param("quantity") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCampaignItem i
               set i.reservedQuantity = i.reservedQuantity - :quantity,
                   i.soldQuantity = i.soldQuantity + :quantity
             where i.id = :itemId and i.reservedQuantity >= :quantity
            """)
    int confirmQuota(@Param("itemId") Long itemId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCampaignItem i
               set i.reservedQuantity = i.reservedQuantity - :quantity
             where i.id = :itemId and i.reservedQuantity >= :quantity
            """)
    int releaseQuota(@Param("itemId") Long itemId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCampaignItem i
               set i.soldQuantity = i.soldQuantity - :quantity
             where i.id = :itemId and i.soldQuantity >= :quantity
            """)
    int reverseSoldQuota(@Param("itemId") Long itemId, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SaleCampaignItem i
               set i.quota = i.quota + :additional
             where i.id = :itemId
               and i.campaign.id = :campaignId
               and i.campaign.type = vn.conganh.commercial.feature.salecampaign.SaleCampaignType.FLASH
               and i.quota is not null
            """)
    int increaseQuota(
            @Param("campaignId") Long campaignId,
            @Param("itemId") Long itemId,
            @Param("additional") int additional);
}
