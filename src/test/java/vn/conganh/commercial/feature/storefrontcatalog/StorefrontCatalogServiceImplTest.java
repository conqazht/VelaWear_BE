package vn.conganh.commercial.feature.storefrontcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogEvaluation.Candidate;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@ExtendWith(MockitoExtension.class)
@DisplayName("Storefront catalog service")
class StorefrontCatalogServiceImplTest {

    @Mock
    private StorefrontCatalogLoader catalogLoader;

    @Mock
    private StorefrontCatalogFilterEngine filterEngine;

    @Mock
    private StorefrontCatalogProductMapper productMapper;

    private StorefrontCatalogServiceImpl catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new StorefrontCatalogServiceImpl(catalogLoader, filterEngine, productMapper);
    }

    @Test
    @DisplayName("Rejects a minimum price greater than the maximum price before loading catalog data")
    void getProducts_invalidPriceRange_throwsBadRequest() {
        StorefrontProductQuery query = query(new BigDecimal("200"), new BigDecimal("100"), 1, 12);

        assertThatThrownBy(() -> catalogService.getProducts(query, "vi"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Minimum price");
        verify(catalogLoader, never()).load("vi");
    }

    @Test
    @DisplayName("Slices the fully evaluated result with one-based pagination")
    void getProducts_secondPage_returnsRemainingCandidateAndAccurateMeta() {
        StorefrontProductQuery query = query(null, null, 2, 2);
        StorefrontCatalogSnapshot snapshot = new StorefrontCatalogSnapshot(List.of(), Map.of(), Map.of());
        Candidate first = new Candidate(null, List.of(), null, 0, BigDecimal.ZERO);
        Candidate second = new Candidate(null, List.of(), null, 0, BigDecimal.ZERO);
        Candidate third = new Candidate(null, List.of(), null, 0, BigDecimal.ZERO);
        StorefrontCatalogResponse.Facets facets = new StorefrontCatalogResponse.Facets(
                List.of(),
                List.of(),
                List.of(),
                new StorefrontCatalogResponse.PriceRange(null, null));
        when(catalogLoader.load("vi")).thenReturn(snapshot);
        when(filterEngine.evaluate(snapshot, query)).thenReturn(new StorefrontCatalogEvaluation(
                List.of(first, second, third),
                facets));

        StorefrontCatalogResponse result = catalogService.getProducts(query, "vi");

        assertThat(result.result()).hasSize(1);
        assertThat(result.meta().page()).isEqualTo(2);
        assertThat(result.meta().pageSize()).isEqualTo(2);
        assertThat(result.meta().pages()).isEqualTo(2);
        assertThat(result.meta().total()).isEqualTo(3);
        verify(productMapper).toResponse(third, snapshot);
    }

    private StorefrontProductQuery query(
            BigDecimal minimum,
            BigDecimal maximum,
            Integer page,
            Integer size) {
        return new StorefrontProductQuery(
                null,
                null,
                null,
                null,
                minimum,
                maximum,
                "featured",
                page,
                size,
                "vi");
    }
}
