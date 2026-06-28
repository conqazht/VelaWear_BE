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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;
    private final CatalogLocaleResolver catalogLocaleResolver;

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

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestBody @Valid CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(categoryService.createCategory(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
