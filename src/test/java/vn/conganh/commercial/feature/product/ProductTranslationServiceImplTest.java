package vn.conganh.commercial.feature.product;

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
import vn.conganh.commercial.feature.product.dto.ProductTranslationRequest;
import vn.conganh.commercial.feature.product.dto.UpdateProductTranslationsRequest;

@ExtendWith(MockitoExtension.class)
class ProductTranslationServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock ProductTranslationRepository translationRepository;
    @Mock CatalogLocaleResolver localeResolver;

    private ProductTranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductTranslationServiceImpl(
                productRepository,
                translationRepository,
                localeResolver);
    }

    @Test
    void updateTranslations_upsertsAtomicallyAndMirrorsVietnameseCoreFields() {
        Product product = product();
        List<ProductTranslation> stored = new ArrayList<>();
        ProductTranslationRequest vi = request("vi", "Tên mới", "ten-moi");
        ProductTranslationRequest en = request("en", "English name", "english-name");
        when(productRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(localeResolver.requireEnabledLocale("vi")).thenReturn("vi");
        when(localeResolver.requireEnabledLocale("en")).thenReturn("en");
        when(translationRepository.findByLocaleCodeAndSlug(any(), any())).thenReturn(Optional.empty());
        when(translationRepository.findByProductIdAndLocaleCode(any(), any())).thenReturn(Optional.empty());
        when(productRepository.findBySlug("ten-moi")).thenReturn(Optional.empty());
        when(translationRepository.save(any())).thenAnswer(invocation -> {
            ProductTranslation translation = invocation.getArgument(0);
            stored.add(translation);
            return translation;
        });
        when(translationRepository.findByProductId(1L)).thenAnswer(ignored -> stored);

        var response = service.updateTranslations(
                1L,
                new UpdateProductTranslationsRequest(List.of(en, vi)));

        assertThat(response.translations()).extracting(item -> item.localeCode())
                .containsExactly("vi", "en");
        assertThat(product.getName()).isEqualTo("Tên mới");
        assertThat(product.getSlug()).isEqualTo("ten-moi");
        assertThat(product.getDescription()).isEqualTo("Description vi");
        verify(productRepository).save(product);
        verify(translationRepository).flush();
    }

    @Test
    void deleteTranslation_defaultLocaleIsRejectedBeforeDelete() {
        when(productRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product()));
        when(localeResolver.requireEnabledLocale("vi")).thenReturn("vi");

        assertThatThrownBy(() -> service.deleteTranslation(1L, "vi"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("default locale");
        verify(translationRepository, never()).delete(any());
    }

    @Test
    void updateTranslations_duplicateNormalizedLocaleIsRejected() {
        when(productRepository.findWithLockByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product()));
        when(localeResolver.requireEnabledLocale("en")).thenReturn("en");

        var request = request("en", "English name", "english-name");
        assertThatThrownBy(() -> service.updateTranslations(
                1L,
                new UpdateProductTranslationsRequest(List.of(request, request))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Duplicate locale");
    }

    private Product product() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Tên cũ");
        product.setSlug("ten-cu");
        product.setDescription("Mô tả cũ");
        product.setStatus("ACTIVE");
        return product;
    }

    private ProductTranslationRequest request(String locale, String name, String slug) {
        return new ProductTranslationRequest(
                locale,
                name,
                slug,
                "Short " + locale,
                "Description " + locale,
                "Cotton",
                "Hand wash",
                "SEO " + locale,
                "SEO description " + locale);
    }
}
