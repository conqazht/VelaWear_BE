package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import vn.conganh.commercial.feature.catalog.CatalogDisplayService;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.dto.EndAndCloneSaleCampaignRequest;
import vn.conganh.commercial.feature.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class SaleCampaignServiceImplLocalizationTest {

    @Mock SaleCampaignRepository campaignRepository;
    @Mock SaleCampaignItemRepository itemRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductImageRepository productImageRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductTranslationRepository productTranslationRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock CategoryTranslationRepository categoryTranslationRepository;
    @Mock VariantPricingService variantPricingService;
    @Mock SaleCampaignTranslationRepository campaignTranslationRepository;
    @Mock UserRepository userRepository;

    private SaleCampaignServiceImpl service;
    private CatalogDisplayService catalogDisplayService;

    @BeforeEach
    void setUp() {
        SaleCampaignValidator validator = new SaleCampaignValidator(
                itemRepository, variantRepository, productRepository, campaignRepository);
        catalogDisplayService = new CatalogDisplayService(
                productTranslationRepository,
                categoryTranslationRepository,
                categoryRepository,
                productImageRepository,
                variantRepository,
                variantPricingService,
                campaignTranslationRepository);
        service = new SaleCampaignServiceImpl(
                campaignRepository,
                itemRepository,
                campaignTranslationRepository,
                userRepository,
                validator,
                catalogDisplayService);
    }

    @Test
    void getPublicByCode_fallsBackPerFieldAndExposesTranslationLocales() {
        SaleCampaign campaign = campaign();
        SaleCampaignTranslation vi = translation("vi", "Khuyến mãi", "Mô tả tiếng Việt");
        SaleCampaignTranslation en = translation("en", "English sale", " ");
        when(campaignRepository.findDetailedByCode("SALE")).thenReturn(Optional.of(campaign));
        when(campaignTranslationRepository.findByCampaignIdIn(List.of(1L))).thenReturn(List.of(en, vi));

        var response = service.getPublicByCode("SALE", "en");

        assertThat(response.name()).isEqualTo("English sale");
        assertThat(response.description()).isEqualTo("Mô tả tiếng Việt");
        assertThat(response.translationLocales()).containsExactly("vi", "en");
    }

    @Test
    void getPublic_bulkLoadsTranslationsOnceForMultipleCampaigns() {
        SaleCampaign first = campaign();
        SaleCampaign second = campaign();
        ReflectionTestUtils.setField(second, "id", 2L);
        second.setCode("SALE-2");
        attachProduct(first, 10L, 100L);
        attachProduct(second, 20L, 200L);
        when(campaignRepository.findPublicCampaigns(any(), any())).thenReturn(List.of(first, second));
        when(campaignTranslationRepository.findByCampaignIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(productImageRepository.findByProductIdIn(List.of(10L, 20L))).thenReturn(List.of());
        when(productTranslationRepository.findByProductIdIn(List.of(10L, 20L))).thenReturn(List.of());

        var response = service.getPublic(null, List.of(), "en");

        assertThat(response.campaigns()).hasSize(2);
        verify(campaignTranslationRepository, times(1)).findByCampaignIdIn(List.of(1L, 2L));
        verify(campaignTranslationRepository, never()).findByCampaignId(any());
        verify(productImageRepository, times(1)).findByProductIdIn(List.of(10L, 20L));
        verify(productTranslationRepository, times(1)).findByProductIdIn(List.of(10L, 20L));
    }

    @Test
    void endAndClone_copiesAllLocalesAndKeepsVietnameseMirrorAligned() {
        SaleCampaign original = campaign();
        SaleCampaignTranslation vi = translation("vi", "Khuyến mãi", "Mô tả tiếng Việt");
        SaleCampaignTranslation en = translation("en", "English sale", "English description");
        List<SaleCampaignTranslation> copies = new ArrayList<>();
        when(campaignRepository.findWithLockById(1L)).thenReturn(Optional.of(original));
        when(campaignRepository.findDetailedByCode("CLONE")).thenReturn(Optional.empty());
        when(campaignRepository.saveAndFlush(any(SaleCampaign.class))).thenAnswer(invocation -> {
            SaleCampaign saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 2L);
            }
            return saved;
        });
        when(campaignTranslationRepository.findByCampaignId(1L)).thenReturn(List.of(vi, en));
        when(campaignTranslationRepository.save(any())).thenAnswer(invocation -> {
            SaleCampaignTranslation saved = invocation.getArgument(0);
            copies.add(saved);
            return saved;
        });
        when(campaignTranslationRepository.findByCampaignIdIn(List.of(2L)))
                .thenAnswer(ignored -> List.copyOf(copies));
        Instant startsAt = Instant.now().plusSeconds(7200);

        var response = service.endAndClone(
                1L,
                new EndAndCloneSaleCampaignRequest(
                        0L,
                        "CLONE",
                        "Khuyến mãi mới",
                        startsAt,
                        startsAt.plusSeconds(3600)),
                null);

        assertThat(response.translationLocales()).containsExactly("vi", "en");
        assertThat(copies).anySatisfy(copy -> {
            assertThat(copy.getLocaleCode()).isEqualTo("vi");
            assertThat(copy.getName()).isEqualTo("Khuyến mãi mới");
        }).anySatisfy(copy -> {
            assertThat(copy.getLocaleCode()).isEqualTo("en");
            assertThat(copy.getName()).isEqualTo("English sale");
        });
    }

    private SaleCampaign campaign() {
        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 1L);
        campaign.setCode("SALE");
        campaign.setName("Core sale");
        campaign.setDescription("Core description");
        campaign.setType(SaleCampaignType.STANDARD);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(3600));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        return campaign;
    }

    private SaleCampaignTranslation translation(String localeCode, String name, String description) {
        SaleCampaignTranslation translation = new SaleCampaignTranslation();
        translation.setCampaignId(1L);
        translation.setLocaleCode(localeCode);
        translation.setName(name);
        translation.setDescription(description);
        return translation;
    }

    private void attachProduct(SaleCampaign campaign, Long productId, Long variantId) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setName("Product " + productId);
        product.setSlug("product-" + productId);
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        variant.setProduct(product);
        variant.setSku("SKU-" + variantId);
        variant.setStockQuantity(5);
        SaleCampaignItem item = new SaleCampaignItem();
        item.setVariant(variant);
        campaign.replaceItems(List.of(item));
    }
}
