package vn.conganh.commercial.feature.storefrontcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.GlobalExceptionHandler;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@ExtendWith(MockitoExtension.class)
@DisplayName("Storefront catalog controller")
class StorefrontCatalogControllerTest {

    @Mock
    private StorefrontCatalogService catalogService;

    @Mock
    private CatalogLocaleResolver localeResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StorefrontCatalogController controller = new StorefrontCatalogController(catalogService, localeResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Binds CSV facet parameters and returns the stable response envelope")
    void getProducts_csvFilters_returnsCatalogEnvelope() throws Exception {
        when(localeResolver.resolve("vi", "vi-VN")).thenReturn("vi");
        when(catalogService.getProducts(any(), org.mockito.ArgumentMatchers.eq("vi")))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/storefront/products")
                        .queryParam("categorySlugs", "ao,ao-khoac")
                        .queryParam("colorIds", "1,2")
                        .queryParam("sizeIds", "3,4")
                        .queryParam("sort", "price-asc")
                        .queryParam("page", "2")
                        .queryParam("size", "24")
                        .queryParam("locale", "vi")
                        .header("Accept-Language", "vi-VN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.meta.page").value(1))
                .andExpect(jsonPath("$.data.facets.categories").isArray());

        ArgumentCaptor<StorefrontProductQuery> captor = ArgumentCaptor.forClass(StorefrontProductQuery.class);
        verify(catalogService).getProducts(captor.capture(), org.mockito.ArgumentMatchers.eq("vi"));
        StorefrontProductQuery query = captor.getValue();
        assertThat(query.categorySlugs()).containsExactly("ao", "ao-khoac");
        assertThat(query.colorIds()).containsExactly(1L, 2L);
        assertThat(query.sizeIds()).containsExactly(3L, 4L);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(24);
    }

    @Test
    @DisplayName("Rejects a page size greater than the public maximum")
    void getProducts_pageSizeAboveMaximum_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/storefront/products").queryParam("size", "61"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

        verify(catalogService, never()).getProducts(any(), any());
    }

    private StorefrontCatalogResponse emptyResponse() {
        return new StorefrontCatalogResponse(
                List.of(),
                new ResultPaginationDTO.Meta(1, 12, 0, 0),
                new StorefrontCatalogResponse.Facets(
                        List.of(),
                        List.of(),
                        List.of(),
                        new StorefrontCatalogResponse.PriceRange(null, null)));
    }
}
