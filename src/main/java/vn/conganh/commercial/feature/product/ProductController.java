package vn.conganh.commercial.feature.product;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.ProductTranslationsResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductTranslationsRequest;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management endpoints")
public class ProductController {

    private final ProductService productService;
    private final ProductTranslationService productTranslationService;
    private final CatalogLocaleResolver catalogLocaleResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getProducts(
            @ParameterObject ProductFilterRequest filter,
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(productService.getAllProducts(filter, pageable, resolvedLocale)));
    }

    @GetMapping(path = "/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(productService.getProductBySlug(slug, resolvedLocale)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable Long id,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(productService.getProductById(id, resolvedLocale)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody @Valid CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(productService.createProduct(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateProduct(id, request)));
    }

    @PatchMapping(path = "/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateStatus(id, request)));
    }

    @GetMapping(path = "/{id}/translations")
    public ResponseEntity<ApiResponse<ProductTranslationsResponse>> getTranslations(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productTranslationService.getTranslations(id)));
    }

    @PutMapping(path = "/{id}/translations")
    public ResponseEntity<ApiResponse<ProductTranslationsResponse>> updateTranslations(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProductTranslationsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productTranslationService.updateTranslations(id, request)));
    }

    @DeleteMapping(path = "/{id}/translations/{locale}")
    public ResponseEntity<ApiResponse<Void>> deleteTranslation(
            @PathVariable Long id,
            @PathVariable String locale) {
        productTranslationService.deleteTranslation(id, locale);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
