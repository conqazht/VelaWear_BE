package vn.conganh.commercial.feature.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Category - CategoryServiceImpl")
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryTranslationRepository categoryTranslationRepository;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, categoryTranslationRepository);
    }

    @Nested
    @DisplayName("Create category")
    class CreateCategory {

        @Test
        @DisplayName("createCategory - tạo category thành công khi slug chưa tồn tại")
        void createCategory_validRequest_returnsCategoryResponse() {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(null, "Shoes", "shoes", 1, "ACTIVE");
            when(categoryRepository.existsBySlug("shoes")).thenReturn(false);
            when(categoryTranslationRepository.existsByLocaleCodeAndSlug("vi", "shoes")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
                Category category = invocation.getArgument(0);
                ReflectionTestUtils.setField(category, "id", 1L);
                return category;
            });
            when(categoryTranslationRepository.findByCategoryIdAndLocaleCode(1L, "vi")).thenReturn(Optional.empty());
            when(categoryTranslationRepository.save(any(CategoryTranslation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CategoryResponse response = categoryService.createCategory(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Shoes");
            assertThat(response.slug()).isEqualTo("shoes");
        }

        @Test
        @DisplayName("createCategory - không gọi save khi slug đã tồn tại")
        void createCategory_duplicateSlug_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(null, "Shoes", "shoes", 1, "ACTIVE");
            when(categoryRepository.existsBySlug("shoes")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("createCategory - không gọi save khi slug bản dịch vi đã tồn tại")
        void createCategory_duplicateVietnameseTranslationSlug_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(null, "Shoes", "shoes", 1, "ACTIVE");
            when(categoryRepository.existsBySlug("shoes")).thenReturn(false);
            when(categoryTranslationRepository.existsByLocaleCodeAndSlug("vi", "shoes")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> categoryService.createCategory(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read category")
    class ReadCategory {

        @Test
        @DisplayName("getAllCategories - trả về danh sách category")
        void getAllCategories_existingCategories_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(categoryRepository.findAll(ArgumentMatchers.<Specification<Category>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(category(1L, "Shoes", "shoes")), pageable, 1));
            when(categoryTranslationRepository.findByCategoryIdIn(anyCollection()))
                    .thenReturn(List.of());

            // Act
            ResultPaginationDTO responses = categoryService.getAllCategories(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("slug").containsExactly("shoes");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getCategoryById - ném ResourceNotFoundException khi không tìm thấy category")
        void getCategoryById_missingCategory_throwsResourceNotFoundException() {
            // Arrange
            when(categoryRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update category")
    class UpdateCategory {

        @Test
        @DisplayName("updateCategory - cập nhật category thành công khi id tồn tại")
        void updateCategory_existingCategory_returnsUpdatedResponse() {
            // Arrange
            Category category = category(1L, "Old", "old");
            UpdateCategoryRequest request = new UpdateCategoryRequest(null, "New", 2, "INACTIVE");
            when(categoryRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category));
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryTranslationRepository.findByCategoryIdAndLocaleCode(1L, "vi")).thenReturn(Optional.empty());
            when(categoryTranslationRepository.save(any(CategoryTranslation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CategoryResponse response = categoryService.updateCategory(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("New");
            assertThat(response.slug()).isEqualTo("old");
            assertThat(response.status()).isEqualTo("INACTIVE");
            assertThat(response.description()).isNull();
            assertThat(response.seoTitle()).isEqualTo("New");
            assertThat(response.seoDescription()).isNull();
        }

        @Test
        @DisplayName("updateCategory - giữ nguyên metadata bản dịch vi không có trong request")
        void updateCategory_existingVietnameseTranslation_preservesOmittedTranslationFields() {
            // Arrange
            Category category = category(1L, "Old", "old");
            CategoryTranslation translation = categoryTranslation(1L, "vi", "Tên cũ", "old");
            CategoryTranslation english = categoryTranslation(1L, "en", "Old English", "old-english");
            translation.setDescription("Mô tả được giữ");
            translation.setSeoTitle("SEO title được giữ");
            translation.setSeoDescription("SEO description được giữ");
            UpdateCategoryRequest request = new UpdateCategoryRequest(2L, "New", 3, "INACTIVE");
            when(categoryRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category));
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryTranslationRepository.findByCategoryIdAndLocaleCode(1L, "vi"))
                    .thenReturn(Optional.of(translation));
            when(categoryTranslationRepository.save(any(CategoryTranslation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryTranslationRepository.findByCategoryId(1L)).thenReturn(List.of(english, translation));

            // Act
            CategoryResponse response = categoryService.updateCategory(1L, request);

            // Assert
            assertThat(category.getParentId()).isEqualTo(2L);
            assertThat(category.getName()).isEqualTo("New");
            assertThat(category.getSortOrder()).isEqualTo(3);
            assertThat(category.getStatus()).isEqualTo("INACTIVE");
            assertThat(translation.getName()).isEqualTo("New");
            assertThat(translation.getDescription()).isEqualTo("Mô tả được giữ");
            assertThat(translation.getSeoTitle()).isEqualTo("SEO title được giữ");
            assertThat(translation.getSeoDescription()).isEqualTo("SEO description được giữ");
            assertThat(response.description()).isEqualTo("Mô tả được giữ");
            assertThat(response.seoTitle()).isEqualTo("SEO title được giữ");
            assertThat(response.seoDescription()).isEqualTo("SEO description được giữ");
            assertThat(response.translationLocales()).containsExactly("vi", "en");
        }
    }

    @Nested
    @DisplayName("Read localized category")
    class ReadLocalizedCategory {

        @Test
        @DisplayName("getCategoryById - fallback về bản dịch vi khi locale yêu cầu bị thiếu")
        void getCategoryById_missingRequestedTranslation_fallsBackToVietnamese() {
            // Arrange
            Category category = category(1L, "Core name", "core-slug");
            CategoryTranslation vi = categoryTranslation(1L, "vi", "Tên danh mục", "ten-danh-muc");
            when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category));
            when(categoryTranslationRepository.findByCategoryIdIn(List.of(1L))).thenReturn(List.of(vi));

            // Act
            CategoryResponse response = categoryService.getCategoryById(1L, "en");

            // Assert
            assertThat(response.name()).isEqualTo("Tên danh mục");
            assertThat(response.slug()).isEqualTo("ten-danh-muc");
        }

        @Test
        @DisplayName("getCategoryBySlug - tìm theo slug bản dịch")
        void getCategoryBySlug_localizedSlug_returnsLocalizedResponse() {
            // Arrange
            Category category = category(1L, "Core name", "core-slug");
            CategoryTranslation vi = categoryTranslation(1L, "vi", "Tên danh mục", "ten-danh-muc");
            when(categoryTranslationRepository.findByLocaleCodeAndSlug("vi", "ten-danh-muc"))
                    .thenReturn(Optional.of(vi));
            when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category));
            when(categoryTranslationRepository.findByCategoryIdIn(List.of(1L))).thenReturn(List.of(vi));

            // Act
            CategoryResponse response = categoryService.getCategoryBySlug("ten-danh-muc", "vi");

            // Assert
            assertThat(response.name()).isEqualTo("Tên danh mục");
            assertThat(response.slug()).isEqualTo("ten-danh-muc");
        }
    }

    private Category category(Long id, String name, String slug) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        category.setName(name);
        category.setSlug(slug);
        category.setSortOrder(1);
        category.setStatus("ACTIVE");
        return category;
    }

    private CategoryTranslation categoryTranslation(Long categoryId, String localeCode, String name, String slug) {
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategoryId(categoryId);
        translation.setLocaleCode(localeCode);
        translation.setName(name);
        translation.setSlug(slug);
        translation.setDescription("Mô tả");
        translation.setSeoTitle(name);
        translation.setSeoDescription("SEO");
        return translation;
    }
}
