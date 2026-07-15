package vn.conganh.commercial.feature.category;

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
import vn.conganh.commercial.feature.catalog.i18n.generation.EnglishContentSuggestionService;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionResponse;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CategoryTranslationsResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryTranslationsRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryTranslationService categoryTranslationService;
    private final CatalogLocaleResolver catalogLocaleResolver;
    private final EnglishContentSuggestionService englishContentSuggestionService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getCategories(
            @ParameterObject CategoryFilterRequest filter,
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategories(filter, pageable, resolvedLocale)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryById(id, resolvedLocale)));
    }

    @GetMapping(path = "/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String resolvedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryBySlug(slug, resolvedLocale)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(categoryService.createCategory(request)));
    }

    @PostMapping(path = "/translation-suggestions/en")
    public ResponseEntity<ApiResponse<CategoryEnglishSuggestionResponse>> suggestEnglishContent(
            @RequestBody @Valid CategoryEnglishSuggestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                englishContentSuggestionService.suggestCategory(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request)));
    }

    @PatchMapping(path = "/{id}/status")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateStatus(id, request)));
    }

    @GetMapping(path = "/{id}/translations")
    public ResponseEntity<ApiResponse<CategoryTranslationsResponse>> getTranslations(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryTranslationService.getTranslations(id)));
    }

    @PutMapping(path = "/{id}/translations")
    public ResponseEntity<ApiResponse<CategoryTranslationsResponse>> updateTranslations(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryTranslationsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryTranslationService.updateTranslations(id, request)));
    }

    @DeleteMapping(path = "/{id}/translations/{locale}")
    public ResponseEntity<ApiResponse<Void>> deleteTranslation(
            @PathVariable Long id,
            @PathVariable String locale) {
        categoryTranslationService.deleteTranslation(id, locale);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
