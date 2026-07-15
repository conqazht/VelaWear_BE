package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignTranslationsRequest;

@ExtendWith(MockitoExtension.class)
class SaleCampaignTranslationServiceImplTest {

    @Mock SaleCampaignRepository campaignRepository;
    @Mock SaleCampaignTranslationRepository translationRepository;
    @Mock CatalogLocaleResolver localeResolver;

    private SaleCampaignTranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SaleCampaignTranslationServiceImpl(
                campaignRepository,
                translationRepository,
                localeResolver);
    }

    @Test
    void updateTranslations_incrementsParentVersionAndReturnsNewVersion() {
        SaleCampaign campaign = campaign(3L);
        List<SaleCampaignTranslation> stored = new ArrayList<>();
        when(campaignRepository.findWithLockById(1L)).thenReturn(Optional.of(campaign));
        when(localeResolver.requireEnabledLocale("en")).thenReturn("en");
        when(translationRepository.findByCampaignIdAndLocaleCode(1L, "en")).thenReturn(Optional.empty());
        when(translationRepository.save(any())).thenAnswer(invocation -> {
            SaleCampaignTranslation translation = invocation.getArgument(0);
            stored.add(translation);
            return translation;
        });
        when(campaignRepository.bumpVersion(1L, 3L)).thenAnswer(ignored -> {
            campaign.setVersion(4L);
            return 1;
        });
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(translationRepository.findByCampaignId(1L)).thenAnswer(ignored -> stored);

        var response = service.updateTranslations(
                1L,
                new UpdateSaleCampaignTranslationsRequest(
                        3L,
                        List.of(new SaleCampaignTranslationRequest(
                                "en", "English sale", "Description"))));

        assertThat(response.version()).isEqualTo(4L);
        assertThat(response.translations()).singleElement()
                .satisfies(translation -> assertThat(translation.name()).isEqualTo("English sale"));
        verify(campaignRepository).bumpVersion(1L, 3L);
    }

    @Test
    void deleteTranslation_defaultLocaleDoesNotIncrementVersion() {
        SaleCampaign campaign = campaign(3L);
        when(campaignRepository.findWithLockById(1L)).thenReturn(Optional.of(campaign));
        when(localeResolver.requireEnabledLocale("vi")).thenReturn("vi");

        assertThatThrownBy(() -> service.deleteTranslation(1L, "vi", 3L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("default locale");
        verify(campaignRepository, never()).bumpVersion(any(), any(Long.class));
    }

    @Test
    void updateTranslations_endedCampaignIsImmutable() {
        SaleCampaign campaign = campaign(3L);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(7200));
        campaign.setEndsAt(Instant.now().minusSeconds(3600));
        when(campaignRepository.findWithLockById(1L)).thenReturn(Optional.of(campaign));

        CodedBusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                CodedBusinessException.class,
                () -> service.updateTranslations(
                        1L,
                        new UpdateSaleCampaignTranslationsRequest(
                                3L,
                                List.of(new SaleCampaignTranslationRequest(
                                        "en", "English sale", "Description")))));

        assertThat(error.getCode()).isEqualTo("CAMPAIGN_HISTORY_IMMUTABLE");
        verify(translationRepository, never()).save(any());
        verify(campaignRepository, never()).bumpVersion(any(), any(Long.class));
    }

    @Test
    void deleteTranslation_cancelledCampaignIsImmutable() {
        SaleCampaign campaign = campaign(3L);
        campaign.setStatus(SaleCampaignStatus.CANCELLED);
        when(campaignRepository.findWithLockById(1L)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.deleteTranslation(1L, "en", 3L))
                .isInstanceOf(CodedBusinessException.class)
                .extracting("code")
                .isEqualTo("CAMPAIGN_HISTORY_IMMUTABLE");
        verify(localeResolver, never()).requireEnabledLocale(any());
    }

    private SaleCampaign campaign(long version) {
        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 1L);
        campaign.setCode("SALE");
        campaign.setName("Khuyến mãi");
        campaign.setType(SaleCampaignType.STANDARD);
        campaign.setStatus(SaleCampaignStatus.DRAFT);
        campaign.setStartsAt(Instant.now().plusSeconds(3600));
        campaign.setEndsAt(Instant.now().plusSeconds(7200));
        campaign.setVersion(version);
        return campaign;
    }
}
