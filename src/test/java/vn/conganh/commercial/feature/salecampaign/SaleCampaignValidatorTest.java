package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignItemRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module SaleCampaign - SaleCampaignValidator")
class SaleCampaignValidatorTest {

    @Mock
    private SaleCampaignItemRepository itemRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SaleCampaignRepository campaignRepository;

    @InjectMocks
    private SaleCampaignValidator validator;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setStatus("ACTIVE");

        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 10L);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(100));
        variant.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("validateItem - throws exception when promotional price >= reference price")
    void validateItem_throwsWhenPromoPriceGreaterOrEqual() {
        SaleCampaignItemRequest request = new SaleCampaignItemRequest(10L, BigDecimal.valueOf(100), 10, 2);

        assertThatThrownBy(() -> validator.validateItem(SaleCampaignType.FLASH, BigDecimal.valueOf(100), request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Promotional price must be lower than base price");
    }

    @Test
    @DisplayName("validateTime - throws exception when endsAt is not after startsAt")
    void validateTime_throwsWhenEndNotAfterStart() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> validator.validateTime(now, now))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("endsAt must be after startsAt");
    }

    @Test
    @DisplayName("buildItems - successfully builds campaign items when valid")
    void buildItems_successfullyBuildsItems() {
        SaleCampaignItemRequest request = new SaleCampaignItemRequest(10L, BigDecimal.valueOf(80), 10, 2);

        when(variantRepository.findAllByIdsWithLock(List.of(10L))).thenReturn(List.of(variant));
        when(productRepository.findAllWithLockByIdIn(List.of(1L))).thenReturn(List.of(product));

        List<SaleCampaignItem> items = validator.buildItems(SaleCampaignType.FLASH, List.of(request));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getPromotionalPrice()).isEqualTo(BigDecimal.valueOf(80));
    }
}
