package vn.conganh.commercial.feature.salecampaign;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface SaleCampaignRepository
        extends JpaRepository<SaleCampaign, Long>, JpaSpecificationExecutor<SaleCampaign> {

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    @Query("select distinct c from SaleCampaign c where c.id = :id")
    Optional<SaleCampaign> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    @Query("select distinct c from SaleCampaign c where lower(c.code) = lower(:code)")
    Optional<SaleCampaign> findDetailedByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    @Query("select distinct c from SaleCampaign c where c.id = :id")
    Optional<SaleCampaign> findWithLockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SaleCampaign c where c.id in :ids order by c.id asc")
    List<SaleCampaign> findAllStatesWithLockByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            select c from SaleCampaign c
            where c.status = vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus.PUBLISHED
              and (:type is null or c.type = :type)
              and c.endsAt > :now
            order by c.startsAt asc, c.id asc
            """)
    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    List<SaleCampaign> findPublicCampaigns(@Param("type") SaleCampaignType type, @Param("now") Instant now);

    Page<SaleCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SaleCampaign c set c.version = c.version + 1 where c.id = :id and c.version = :version")
    int bumpVersion(@Param("id") Long id, @Param("version") long version);
}
