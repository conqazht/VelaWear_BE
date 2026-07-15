package vn.conganh.commercial.feature.catalog.i18n.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.config.GeminiEnglishContentProperties;
import vn.conganh.commercial.exception.EnglishContentGenerationException;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.ProductEnglishSuggestionRequest;

@ExtendWith(MockitoExtension.class)
class EnglishContentSuggestionServiceImplTest {

    @Mock
    private EnglishContentProvider provider;

    private EnglishContentSuggestionServiceImpl service;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new EnglishContentSuggestionServiceImpl(provider, properties(true, "secret", 30_000), validator);
    }

    @Test
    void suggestProduct_usesDefaultModelAndReturnsTypedEnglishContentWithoutSlug() {
        Map<String, String> generated = new LinkedHashMap<>();
        generated.put("name", "Essential Cotton Tee");
        generated.put("shortDescription", "A soft cotton tee.");
        generated.put("description", "English description");
        generated.put("material", "100% cotton");
        generated.put("careInstruction", "Machine wash cold.");
        generated.put("seoTitle", "Essential Cotton Tee");
        generated.put("seoDescription", "English SEO description");
        when(provider.generate(any())).thenReturn(new EnglishContentGenerationResult(generated));

        var response = service.suggestProduct(new ProductEnglishSuggestionRequest(
                null,
                "Áo thun cotton thiết yếu",
                "Áo thun mềm mại",
                "Mô tả tiếng Việt",
                "100% cotton",
                "Giặt máy nước lạnh",
                "Áo thun cotton",
                "Mô tả SEO"));

        assertThat(response.localeCode()).isEqualTo("en");
        assertThat(response.name()).isEqualTo("Essential Cotton Tee");
        assertThat(response).hasNoNullFieldsOrProperties();

        ArgumentCaptor<EnglishContentGenerationRequest> captor =
                ArgumentCaptor.forClass(EnglishContentGenerationRequest.class);
        verify(provider).generate(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(captor.getValue().contentType()).isEqualTo(EnglishContentType.PRODUCT);
        assertThat(captor.getValue().sourceFields())
                .containsEntry("name", "Áo thun cotton thiết yếu")
                .doesNotContainKey("slug")
                .doesNotContainKey("model");
    }

    @Test
    void suggestProduct_acceptsWhitelistedExplicitModel() {
        when(provider.generate(any())).thenAnswer(invocation -> {
            EnglishContentGenerationRequest request = invocation.getArgument(0);
            Map<String, String> fields = new LinkedHashMap<>();
            request.contentType().fields().forEach(field -> fields.put(field,
                    field.equals("name") ? "English name" : null));
            return new EnglishContentGenerationResult(fields);
        });

        service.suggestProduct(new ProductEnglishSuggestionRequest(
                "gemini-3.1-pro-preview", "Tên sản phẩm", null, null, null, null, null, null));

        ArgumentCaptor<EnglishContentGenerationRequest> captor =
                ArgumentCaptor.forClass(EnglishContentGenerationRequest.class);
        verify(provider).generate(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("gemini-3.1-pro-preview");
    }

    @Test
    void suggestProduct_rejectsModelOutsideWhitelist() {
        assertThatThrownBy(() -> service.suggestProduct(new ProductEnglishSuggestionRequest(
                "gemini-untrusted", "Tên sản phẩm", null, null, null, null, null, null)))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> assertThat(((EnglishContentGenerationException) exception).getCode())
                        .isEqualTo("CONTENT_GENERATION_MODEL_NOT_ALLOWED"));
    }

    @Test
    void suggestProduct_whenDisabledReturnsCodedServiceUnavailable() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new EnglishContentSuggestionServiceImpl(provider, properties(false, "", 30_000), validator);

        assertThatThrownBy(() -> service.suggestProduct(new ProductEnglishSuggestionRequest(
                null, "Tên sản phẩm", null, null, null, null, null, null)))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> {
                    EnglishContentGenerationException typed = (EnglishContentGenerationException) exception;
                    assertThat(typed.getCode()).isEqualTo("CONTENT_GENERATION_DISABLED");
                    assertThat(typed.getStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void suggestProduct_rejectsProviderOutputThatViolatesResponseDto() {
        Map<String, String> generated = new LinkedHashMap<>();
        EnglishContentType.PRODUCT.fields().forEach(field -> generated.put(field, null));
        when(provider.generate(any())).thenReturn(new EnglishContentGenerationResult(generated));

        assertThatThrownBy(() -> service.suggestProduct(new ProductEnglishSuggestionRequest(
                null, "Tên sản phẩm", null, null, null, null, null, null)))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> assertThat(((EnglishContentGenerationException) exception).getCode())
                        .isEqualTo("CONTENT_GENERATION_INVALID_RESPONSE"));
    }

    @Test
    void suggestProduct_neverFillsOptionalEnglishFieldWhenVietnameseSourceIsBlank() {
        Map<String, String> generated = new LinkedHashMap<>();
        EnglishContentType.PRODUCT.fields().forEach(field -> generated.put(field,
                field.equals("name") ? "English name" : "Invented by provider"));
        when(provider.generate(any())).thenReturn(new EnglishContentGenerationResult(generated));

        var response = service.suggestProduct(new ProductEnglishSuggestionRequest(
                null,
                "Tên sản phẩm",
                null,
                "   ",
                "\t",
                null,
                "",
                null));

        assertThat(response.name()).isEqualTo("English name");
        assertThat(response.shortDescription()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.material()).isNull();
        assertThat(response.careInstruction()).isNull();
        assertThat(response.seoTitle()).isNull();
        assertThat(response.seoDescription()).isNull();
    }

    @Test
    void suggestProduct_rejectsTotalSourceLargerThanConfiguredLimit() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new EnglishContentSuggestionServiceImpl(provider, properties(true, "secret", 5), validator);

        assertThatThrownBy(() -> service.suggestProduct(new ProductEnglishSuggestionRequest(
                null, "Tên sản phẩm", null, null, null, null, null, null)))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> assertThat(((EnglishContentGenerationException) exception).getCode())
                        .isEqualTo("CONTENT_GENERATION_INPUT_TOO_LARGE"));
    }

    private GeminiEnglishContentProperties properties(boolean enabled, String apiKey, int maxInputCharacters) {
        return new GeminiEnglishContentProperties(
                enabled,
                apiKey,
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-3.1-flash-lite",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                maxInputCharacters,
                16_384);
    }
}
