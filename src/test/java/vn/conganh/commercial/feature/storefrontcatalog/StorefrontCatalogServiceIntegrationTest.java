package vn.conganh.commercial.feature.storefrontcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@DisplayName("Storefront catalog service integration")
class StorefrontCatalogServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StorefrontCatalogService catalogService;

    @Test
    @DisplayName("Seed catalog exposes all public facets and globally sorted effective prices")
    void getProducts_seedCatalog_returnsCompleteFacetsAndEffectivePriceOrder() {
        StorefrontProductQuery query = new StorefrontProductQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                "price-asc",
                1,
                60,
                "vi");

        StorefrontCatalogResponse response = catalogService.getProducts(query, "vi");

        assertThat(response.meta().total()).isEqualTo(100);
        assertThat(response.facets().categories()).hasSize(7);
        assertThat(response.facets().colors()).hasSize(15);
        assertThat(response.facets().sizes()).hasSize(20);
        assertThat(response.facets().priceRange().min()).isNotNull();
        assertThat(response.facets().priceRange().max()).isNotNull();
        List<BigDecimal> effectivePrices = response.result().stream()
                .map(product -> product.pricing().effectivePrice())
                .toList();
        assertThat(effectivePrices).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("Public endpoint accepts anonymous requests and returns one-based metadata")
    void getProducts_missingToken_returnsPublicCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/storefront/products")
                        .queryParam("sort", "newest")
                        .queryParam("page", "1")
                        .queryParam("size", "12")
                        .queryParam("locale", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.result.length()").value(12))
                .andExpect(jsonPath("$.data.meta.page").value(1))
                .andExpect(jsonPath("$.data.meta.total").value(100))
                .andExpect(jsonPath("$.data.facets.categories.length()").value(7))
                .andExpect(jsonPath("$.data.facets.colors.length()").value(15))
                .andExpect(jsonPath("$.data.facets.sizes.length()").value(20));
    }
}
