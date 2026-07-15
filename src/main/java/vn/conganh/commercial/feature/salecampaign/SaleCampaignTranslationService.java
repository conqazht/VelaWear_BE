package vn.conganh.commercial.feature.salecampaign;

import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationsResponse;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignTranslationsRequest;

public interface SaleCampaignTranslationService {

    SaleCampaignTranslationsResponse getTranslations(Long campaignId);

    SaleCampaignTranslationsResponse updateTranslations(
            Long campaignId,
            UpdateSaleCampaignTranslationsRequest request);

    SaleCampaignTranslationsResponse deleteTranslation(Long campaignId, String localeCode, long version);
}
