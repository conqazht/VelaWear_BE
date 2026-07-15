package vn.conganh.commercial.feature.productvariant;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantFilterRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;

@RestController
@RequestMapping("/api/v1/product-variants")
@RequiredArgsConstructor
@Tag(name = "Product Variants", description = "Sellable product variant and inventory endpoints")
public class ProductVariantController {

    private final ProductVariantService productVariantService;
    private final CatalogLocaleResolver catalogLocaleResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getAll(
            @ParameterObject ProductVariantFilterRequest filter,
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String localeCode = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(productVariantService.getAll(filter, pageable, localeCode)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getById(
            @PathVariable Long id,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String localeCode = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(productVariantService.getById(id, localeCode)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductVariantResponse>> create(
            @RequestBody @Valid CreateProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productVariantService.create(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productVariantService.update(id, request)));
    }

    @PatchMapping(path = "/{id}/status")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productVariantService.updateStatus(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productVariantService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
