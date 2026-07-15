package vn.conganh.commercial.feature.category;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

public interface CategoryService {

    ResultPaginationDTO getAllCategories(CategoryFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getAllCategories(CategoryFilterRequest filter, Pageable pageable, String localeCode);

    CategoryResponse getCategoryById(Long id);

    CategoryResponse getCategoryById(Long id, String localeCode);

    CategoryResponse getCategoryBySlug(String slug, String localeCode);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    CategoryResponse updateStatus(Long id, UpdateStatusRequest request);

    void deleteCategory(Long id);
}
