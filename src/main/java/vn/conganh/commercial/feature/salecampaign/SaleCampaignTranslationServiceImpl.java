package vn.conganh.commercial.feature.salecampaign;

import java.util.HashSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationsResponse;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignTranslationsRequest;

@Service
@RequiredArgsConstructor
public class SaleCampaignTranslationServiceImpl implements SaleCampaignTranslationService {

    private final SaleCampaignRepository campaignRepository;
    private final SaleCampaignTranslationRepository translationRepository;
    private final CatalogLocaleResolver localeResolver;

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignTranslationsResponse getTranslations(Long campaignId) {
        SaleCampaign campaign = findCampaign(campaignId);
        return response(campaign);
    }

    @Override
    @Transactional
    public SaleCampaignTranslationsResponse updateTranslations(
            Long campaignId,
            UpdateSaleCampaignTranslationsRequest request) {
        SaleCampaign campaign = findCampaignWithLock(campaignId);
        verifyVersion(campaign, request.version());
        assertTranslationEditable(campaign);
        Set<String> localeCodes = new HashSet<>();
        SaleCampaignTranslationRequest defaultTranslation = null;
        for (SaleCampaignTranslationRequest item : request.translations()) {
            String localeCode = localeResolver.requireEnabledLocale(item.localeCode());
            if (!localeCodes.add(localeCode)) {
                throw new InvalidRequestException("Duplicate locale in translation batch: " + localeCode);
            }
            SaleCampaignTranslation translation = translationRepository
                    .findByCampaignIdAndLocaleCode(campaignId, localeCode)
                    .orElseGet(SaleCampaignTranslation::new);
            apply(translation, campaignId, localeCode, item);
            translationRepository.save(translation);
            if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(localeCode)) {
                defaultTranslation = item;
            }
        }
        translationRepository.flush();
        if (defaultTranslation != null) {
            campaignRepository.updateTranslationMirror(
                    campaignId,
                    defaultTranslation.name().trim(),
                    defaultTranslation.description());
        }
        bumpVersion(campaignId, request.version());
        return response(findCampaign(campaignId));
    }

    @Override
    @Transactional
    public SaleCampaignTranslationsResponse deleteTranslation(
            Long campaignId,
            String localeCode,
            long version) {
        SaleCampaign campaign = findCampaignWithLock(campaignId);
        verifyVersion(campaign, version);
        assertTranslationEditable(campaign);
        String resolvedLocale = localeResolver.requireEnabledLocale(localeCode);
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            throw new InvalidRequestException("The default locale translation cannot be deleted");
        }
        SaleCampaignTranslation translation = translationRepository
                .findByCampaignIdAndLocaleCode(campaignId, resolvedLocale)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SaleCampaignTranslation", "localeCode", resolvedLocale));
        translationRepository.delete(translation);
        translationRepository.flush();
        bumpVersion(campaignId, version);
        return response(findCampaign(campaignId));
    }

    private void apply(
            SaleCampaignTranslation translation,
            Long campaignId,
            String localeCode,
            SaleCampaignTranslationRequest request) {
        translation.setCampaignId(campaignId);
        translation.setLocaleCode(localeCode);
        translation.setName(request.name().trim());
        translation.setDescription(request.description());
    }

    private SaleCampaignTranslationsResponse response(SaleCampaign campaign) {
        List<SaleCampaignTranslationResponse> translations = translationRepository
                .findByCampaignId(campaign.getId()).stream()
                .sorted((left, right) -> compareLocales(left.getLocaleCode(), right.getLocaleCode()))
                .map(SaleCampaignTranslationResponse::fromEntity)
                .toList();
        return new SaleCampaignTranslationsResponse(campaign.getVersion(), translations);
    }

    private int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private void verifyVersion(SaleCampaign campaign, long version) {
        if (campaign.getVersion() != version) {
            throw versionConflict();
        }
    }

    private void bumpVersion(Long campaignId, long version) {
        if (campaignRepository.bumpVersion(campaignId, version) != 1) {
            throw versionConflict();
        }
    }

    private void assertTranslationEditable(SaleCampaign campaign) {
        boolean editable = campaign.getStatus() == SaleCampaignStatus.DRAFT
                || (campaign.getStatus() == SaleCampaignStatus.PUBLISHED
                        && Instant.now().isBefore(campaign.getEndsAt()));
        if (!editable) {
            throw new CodedBusinessException(
                    "CAMPAIGN_HISTORY_IMMUTABLE",
                    "Ended or cancelled campaign translations cannot be changed",
                    HttpStatus.CONFLICT,
                    Map.of());
        }
    }

    private CodedBusinessException versionConflict() {
        return new CodedBusinessException(
                "CAMPAIGN_VERSION_CONFLICT",
                "Campaign was changed by another request",
                HttpStatus.CONFLICT,
                Map.of());
    }

    private SaleCampaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "id", campaignId));
    }

    private SaleCampaign findCampaignWithLock(Long campaignId) {
        return campaignRepository.findWithLockById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "id", campaignId));
    }
}
