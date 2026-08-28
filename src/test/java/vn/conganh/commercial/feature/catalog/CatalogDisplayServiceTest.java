package vn.conganh.commercial.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;
import vn.conganh.commercial.feature.wishlist.dto.WishlistProductSummaryResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Catalog - CatalogDisplayService")
class CatalogDisplayServiceTest {

    @Mock private ProductTranslationRepository productTranslationRepository;
    @Mock private CategoryTranslationRepository categoryTranslationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private VariantPricingService variantPricingService;
    @Mock private SaleCampaignTranslationRepository campaignTranslationRepository;

    @InjectMocks
    private CatalogDisplayService displayService;

    private Product product;
    private Category category;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        category = new Category();
        ReflectionTestUtils.setField(category, "id", 10L);
        category.setName("Default Category");
        category.setSlug("default-category");

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 100L);
        product.setCategoryId(10L);
        product.setName("Default Product");
        product.setSlug("default-product");
        product.setDescription("Default Desc");
        product.setStatus("ACTIVE");

        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 500L);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(200));
    }

    @Nested
    @DisplayName("assembleProductResponse")
    class ProductResponseTests {
        @Test
        void assembleProductResponse_localizesFieldsAndResolvesPricing() {
            ProductTranslation pTransEn = new ProductTranslation();
            pTransEn.setProductId(100L);
            pTransEn.setLocaleCode("en");
            pTransEn.setName("English Product");
            pTransEn.setSlug("english-product");

            CategoryTranslation cTransEn = new CategoryTranslation();
            cTransEn.setCategoryId(10L);
            cTransEn.setLocaleCode("en");
            cTransEn.setName("English Category");
            cTransEn.setSlug("english-category");

            when(productTranslationRepository.findByProductIdIn(List.of(100L)))
                    .thenReturn(List.of(pTransEn));
            when(categoryTranslationRepository.findByCategoryIdIn(List.of(10L)))
                    .thenReturn(List.of(cTransEn));
            when(categoryRepository.findAllById(List.of(10L)))
                    .thenReturn(List.of(category));
            when(productImageRepository.findByProductIdIn(List.of(100L)))
                    .thenReturn(List.of());
            when(productVariantRepository.findByProductIdInAndDeletedAtIsNull(List.of(100L)))
                    .thenReturn(List.of(variant));

            VariantPricing pricing = new VariantPricing(
                    500L, BigDecimal.valueOf(200), BigDecimal.valueOf(150),
                    PriceSource.BASE, null, null, null, 25);
            when(variantPricingService.resolve(List.of(variant)))
                    .thenReturn(Map.of(500L, pricing));

            ProductResponse response = displayService.assembleProductResponse(product, "en");

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.name()).isEqualTo("English Product");
            assertThat(response.slug()).isEqualTo("english-product");
            assertThat(response.categoryName()).isEqualTo("English Category");
            assertThat(response.categorySlug()).isEqualTo("english-category");
            assertThat(response.price()).isEqualTo(BigDecimal.valueOf(200));
        }
    }

    @Nested
    @DisplayName("assembleWishlistSummaries")
    class WishlistSummaryTests {
        @Test
        void assembleWishlistSummaries_buildsSummaryRecord() {
            when(productTranslationRepository.findByProductIdIn(List.of(100L)))
                    .thenReturn(List.of());
            when(categoryTranslationRepository.findByCategoryIdIn(List.of(10L)))
                    .thenReturn(List.of());
            when(categoryRepository.findAllById(List.of(10L)))
                    .thenReturn(List.of(category));
            when(productImageRepository.findByProductIdIn(List.of(100L)))
                    .thenReturn(List.of());
            when(productVariantRepository.findByProductIdInAndDeletedAtIsNull(List.of(100L)))
                    .thenReturn(List.of(variant));

            VariantPricing pricing = new VariantPricing(
                    500L, BigDecimal.valueOf(200), BigDecimal.valueOf(200),
                    PriceSource.BASE, null, null, null, 0);
            when(variantPricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                    .thenReturn(Map.of(500L, pricing));

            Map<Long, WishlistProductSummaryResponse> summaries =
                    displayService.assembleWishlistSummaries(List.of(product), "vi", 1L);

            assertThat(summaries).containsKey(100L);
            WishlistProductSummaryResponse summary = summaries.get(100L);
            assertThat(summary.id()).isEqualTo(100L);
            assertThat(summary.name()).isEqualTo("Default Product");
            assertThat(summary.categoryName()).isEqualTo("Default Category");
            assertThat(summary.price()).isEqualTo(BigDecimal.valueOf(200));
        }
    }

    @Nested
    @DisplayName("resolveThumbnail")
    class ThumbnailTests {
        @Test
        void resolveThumbnail_prefersVariantImageFirst() {
            ProductImage pImg = new ProductImage();
            pImg.setImage("product.jpg");
            pImg.setIsThumbnail(true);

            ProductImage vImg = new ProductImage();
            vImg.setVariant(variant);
            vImg.setImage("variant.jpg");

            String thumb = displayService.resolveThumbnail(variant, List.of(pImg, vImg));

            assertThat(thumb).isEqualTo("variant.jpg");
        }

        @Test
        void resolveThumbnail_fallsBackToThumbnailFlagOnProductLevel() {
            ProductImage img1 = new ProductImage();
            img1.setImage("regular.jpg");
            img1.setIsThumbnail(false);
            img1.setSortOrder(1);

            ProductImage img2 = new ProductImage();
            img2.setImage("thumb.jpg");
            img2.setIsThumbnail(true);
            img2.setSortOrder(2);

            String thumb = displayService.resolveThumbnail(null, List.of(img1, img2));

            assertThat(thumb).isEqualTo("thumb.jpg");
        }
    }
}
