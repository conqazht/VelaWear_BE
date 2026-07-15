package vn.conganh.commercial.feature.salecampaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleCampaignTranslationRepository
        extends JpaRepository<SaleCampaignTranslation, SaleCampaignTranslationId> {

    Optional<SaleCampaignTranslation> findByCampaignIdAndLocaleCode(Long campaignId, String localeCode);

    List<SaleCampaignTranslation> findByCampaignId(Long campaignId);

    List<SaleCampaignTranslation> findByCampaignIdIn(Collection<Long> campaignIds);
}
