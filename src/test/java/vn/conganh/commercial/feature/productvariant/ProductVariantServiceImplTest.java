package vn.conganh.commercial.feature.productvariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.color.ColorRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.size.SizeRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module ProductVariant - ProductVariantServiceImpl")
class ProductVariantServiceImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ColorRepository colorRepository;

    @Mock
    private SizeRepository sizeRepository;

    @Mock
    private VariantPricingService variantPricingService;

    @Mock
    private SaleCampaignItemRepository saleCampaignItemRepository;

    @Mock
    private SaleCampaignTranslationRepository saleCampaignTranslationRepository;

    private ProductVariantServiceImpl productVariantService;

    @BeforeEach
    void setUp() {
        productVariantService = new ProductVariantServiceImpl(
                productVariantRepository, productRepository, colorRepository, sizeRepository,
                variantPricingService, saleCampaignItemRepository, saleCampaignTranslationRepository);
        org.mockito.Mockito.lenient().when(variantPricingService.resolve(any()))
                .thenReturn(java.util.Map.of());
    }

    @Nested
    @DisplayName("Create product variant")
    class CreateProductVariant {

        @Test
        @DisplayName("create - tạo variant thành công và set default stock/status")
        void create_validRequest_returnsVariantResponse() {
            // Arrange
            Product product = product(1L);
            CreateProductVariantRequest request = new CreateProductVariantRequest(
                    1L, "SKU-001", BigDecimal.valueOf(100000), null, null, null, null);
            when(productVariantRepository.existsBySku("SKU-001")).thenReturn(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
                ProductVariant variant = invocation.getArgument(0);
                ReflectionTestUtils.setField(variant, "id", 1L);
                return variant;
            });

            // Act
            ProductVariantResponse response = productVariantService.create(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.sku()).isEqualTo("SKU-001");
            assertThat(response.stockQuantity()).isZero();
            assertThat(response.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("create - không gọi save khi sku đã tồn tại")
        void create_duplicateSku_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateProductVariantRequest request = new CreateProductVariantRequest(
                    1L, "SKU-001", BigDecimal.TEN, 1, null, null, "ACTIVE");
            when(productVariantRepository.existsBySku("SKU-001")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productVariantService.create(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(productVariantRepository, never()).save(any());
        }

        @Test
        @DisplayName("create - ném ResourceNotFoundException khi product không tồn tại")
        void create_missingProduct_throwsResourceNotFoundException() {
            // Arrange
            CreateProductVariantRequest request = new CreateProductVariantRequest(
                    99L, "SKU-001", BigDecimal.TEN, 1, null, null, "ACTIVE");
            when(productVariantRepository.existsBySku("SKU-001")).thenReturn(false);
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productVariantService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("create - liên kết color và size khi được truyền")
        void create_withColorAndSize_returnsVariantResponse() {
            // Arrange
            Product product = product(1L);
            Color color = color(2L, "Black");
            Size size = size(3L, "XL");
            CreateProductVariantRequest request = new CreateProductVariantRequest(
                    1L, "SKU-002", BigDecimal.TEN, 5, 2L, 3L, "ACTIVE");
            when(productVariantRepository.existsBySku("SKU-002")).thenReturn(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(colorRepository.findById(2L)).thenReturn(Optional.of(color));
            when(sizeRepository.findById(3L)).thenReturn(Optional.of(size));
            when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ProductVariantResponse response = productVariantService.create(request);

            // Assert
            assertThat(response.color().name()).isEqualTo("Black");
            assertThat(response.size().name()).isEqualTo("XL");
        }
    }

    @Nested
    @DisplayName("Update product variant status")
    class UpdateProductVariantStatus {

        @Test
        void updateStatus_activeToInactive_checksCampaignGuard() {
            ProductVariant variant = variant(1L, "ACTIVE");
            when(productVariantRepository.findWithLockByIdAndDeletedAtIsNull(1L))
                    .thenReturn(Optional.of(variant));
            when(saleCampaignItemRepository.existsProtectedVariant(any(), any())).thenReturn(true);

            assertThatThrownBy(() -> productVariantService.updateStatus(
                    1L,
                    new UpdateStatusRequest("INACTIVE")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("sale campaign");
            verify(productVariantRepository, never()).save(variant);
        }

        @Test
        void updateStatus_discontinuedSourceIsRejected() {
            ProductVariant variant = variant(1L, "DISCONTINUED");
            when(productVariantRepository.findWithLockByIdAndDeletedAtIsNull(1L))
                    .thenReturn(Optional.of(variant));

            assertThatThrownBy(() -> productVariantService.updateStatus(
                    1L,
                    new UpdateStatusRequest("ACTIVE")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("not allowed");
            verify(productVariantRepository, never()).save(any());
            verify(saleCampaignItemRepository, never()).existsProtectedVariant(any(), any());
        }

        @Test
        void updateStatus_cannotTargetDiscontinued() {
            assertThatThrownBy(() -> productVariantService.updateStatus(
                    1L,
                    new UpdateStatusRequest("DISCONTINUED")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("invalid");
            verify(productVariantRepository, never()).findWithLockByIdAndDeletedAtIsNull(any());
        }
    }

    @Test
    void getById_englishLocale_localizesPricingCampaignName() {
        ProductVariant variant = variant(1L, "ACTIVE");
        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 2L);
        campaign.setCode("SALE");
        campaign.setName("Khuyến mãi");
        SaleCampaignItem item = new SaleCampaignItem();
        item.setCampaign(campaign);
        item.setVariant(variant);
        VariantPricing pricing = new VariantPricing(
                1L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                PriceSource.STANDARD_SALE,
                item,
                null,
                null,
                1);
        SaleCampaignTranslation en = new SaleCampaignTranslation();
        en.setCampaignId(2L);
        en.setLocaleCode("en");
        en.setName("English sale");
        when(productVariantRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(variant));
        when(variantPricingService.resolve(java.util.List.of(variant))).thenReturn(java.util.Map.of(1L, pricing));
        when(saleCampaignTranslationRepository.findByCampaignIdIn(java.util.List.of(2L)))
                .thenReturn(java.util.List.of(en));

        ProductVariantResponse response = productVariantService.getById(1L, "en");

        assertThat(response.pricing().campaignName()).isEqualTo("English sale");
    }

    private Product product(Long id) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName("Product");
        return product;
    }

    private ProductVariant variant(Long id, String status) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setProduct(product(1L));
        variant.setSku("SKU");
        variant.setPrice(BigDecimal.TEN);
        variant.setStockQuantity(1);
        variant.setStatus(status);
        return variant;
    }

    private Color color(Long id, String name) {
        Color color = new Color();
        ReflectionTestUtils.setField(color, "id", id);
        color.setName(name);
        return color;
    }

    private Size size(Long id, String name) {
        Size size = new Size();
        ReflectionTestUtils.setField(size, "id", id);
        size.setName(name);
        return size;
    }
}
