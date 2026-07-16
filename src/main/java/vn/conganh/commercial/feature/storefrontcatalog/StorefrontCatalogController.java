package vn.conganh.commercial.feature.storefrontcatalog;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@RestController
@RequestMapping("/api/v1/storefront")
@RequiredArgsConstructor
@Tag(name = "Storefront Catalog", description = "Public product discovery endpoints")
public class StorefrontCatalogController {

    private final StorefrontCatalogService catalogService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<StorefrontCatalogResponse>> getProducts(
            @Valid @ParameterObject StorefrontProductQuery query,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String localeCode = localeResolver.resolve(query.locale(), acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(catalogService.getProducts(query, localeCode)));
    }
}
