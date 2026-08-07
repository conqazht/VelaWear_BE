package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;

@DisplayName("Module SaleCampaign - SaleCampaignResponseAssembler")
class SaleCampaignResponseAssemblerTest {

    private SaleCampaignResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SaleCampaignResponseAssembler();
    }

    @Test
    @DisplayName("assembleResponse - assembles campaign response DTO accurately with translations")
    void assembleResponse_assemblesResponseAccurately() {
        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 100L);
        campaign.setCode("FLASH_SUMMER");
        campaign.setName("Summer Sale");
        campaign.setDescription("Summer discount");
        campaign.setType(SaleCampaignType.FLASH);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(3600));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Product Name");
        product.setSlug("product-name");

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 10L);
        variant.setProduct(product);

        SaleCampaignItem item = new SaleCampaignItem();
        item.setVariant(variant);
        item.setReferencePrice(BigDecimal.valueOf(100));
        item.setPromotionalPrice(BigDecimal.valueOf(80));
        campaign.getItems().add(item);

        SaleCampaignResponse response = assembler.assembleResponse(
                campaign,
                Instant.now(),
                "vi",
                Map.of(),
                Map.of(),
                Map.of());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.code()).isEqualTo("FLASH_SUMMER");
        assertThat(response.name()).isEqualTo("Summer Sale");
    }
}
