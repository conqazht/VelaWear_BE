package vn.conganh.commercial.feature.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.dto.CategoryTranslationRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryTranslationsRequest;

@ExtendWith(MockitoExtension.class)
class CategoryTranslationServiceImplTest {

    @Mock CategoryRepository categoryRepository;
    @Mock CategoryTranslationRepository translationRepository;
    @Mock CatalogLocaleResolver localeResolver;

    private CategoryTranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryTranslationServiceImpl(
                categoryRepository,
                translationRepository,
                localeResolver);
    }

    @Test
    void updateTranslations_mirrorsVietnameseNameAndSlug() {
        Category category = category();
        List<CategoryTranslation> stored = new ArrayList<>();
        var item = new CategoryTranslationRequest(
                "vi", "Tên mới", "ten-moi", "Mô tả", "SEO", "SEO description");
        when(categoryRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category));
        when(localeResolver.requireEnabledLocale("vi")).thenReturn("vi");
        when(translationRepository.findByLocaleCodeAndSlug("vi", "ten-moi")).thenReturn(Optional.empty());
        when(translationRepository.findByCategoryIdAndLocaleCode(1L, "vi")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlug("ten-moi")).thenReturn(Optional.empty());
        when(translationRepository.save(any())).thenAnswer(invocation -> {
            CategoryTranslation translation = invocation.getArgument(0);
            stored.add(translation);
            return translation;
        });
        when(translationRepository.findByCategoryId(1L)).thenAnswer(ignored -> stored);

        var response = service.updateTranslations(
                1L,
                new UpdateCategoryTranslationsRequest(List.of(item)));

        assertThat(response.translations()).singleElement()
                .satisfies(translation -> assertThat(translation.slug()).isEqualTo("ten-moi"));
        assertThat(category.getName()).isEqualTo("Tên mới");
        assertThat(category.getSlug()).isEqualTo("ten-moi");
        verify(categoryRepository).save(category);
    }

    @Test
    void deleteTranslation_defaultLocaleIsRejected() {
        when(categoryRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(category()));
        when(localeResolver.requireEnabledLocale("vi")).thenReturn("vi");

        assertThatThrownBy(() -> service.deleteTranslation(1L, "vi"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("default locale");
        verify(translationRepository, never()).delete(any());
    }

    private Category category() {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 1L);
        category.setName("Tên cũ");
        category.setSlug("ten-cu");
        category.setStatus("ACTIVE");
        return category;
    }
}
