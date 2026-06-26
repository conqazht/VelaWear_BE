package vn.conganh.commercial.feature.category;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCategories(Pageable pageable) {
        return ResultPaginationDTO.fromPage(categoryRepository.findAllByDeletedAtIsNull(pageable)
                .map(CategoryResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return CategoryResponse.fromEntity(findCategory(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new InvalidRequestException("Category slug already exists");
        }
        Category category = new Category();
        apply(category, request.parentId(), request.name(), request.slug(), request.sortOrder(), request.status());
        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findCategory(id);
        apply(category, request.parentId(), request.name(), category.getSlug(), request.sortOrder(), request.status());
        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        category.setDeletedAt(Instant.now());
        categoryRepository.save(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private void apply(Category category, Long parentId, String name, String slug, int sortOrder, String status) {
        category.setParentId(parentId);
        category.setName(name);
        category.setSlug(slug);
        category.setSortOrder(sortOrder);
        category.setStatus(status);
    }
}
