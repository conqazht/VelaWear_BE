package vn.conganh.commercial.feature.catalog.i18n.generation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.config.GeminiEnglishContentProperties;
import vn.conganh.commercial.exception.EnglishContentGenerationException;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionResponse;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.ProductEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.ProductEnglishSuggestionResponse;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.SaleCampaignEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.SaleCampaignEnglishSuggestionResponse;

@Service
public class EnglishContentSuggestionServiceImpl implements EnglishContentSuggestionService {

    private static final String TARGET_LOCALE = "en";

    private final EnglishContentProvider provider;
    private final GeminiEnglishContentProperties properties;
    private final Validator validator;

    public EnglishContentSuggestionServiceImpl(
            EnglishContentProvider provider,
            GeminiEnglishContentProperties properties,
            Validator validator) {
        this.provider = provider;
        this.properties = properties;
        this.validator = validator;
    }

    @Override
    public ProductEnglishSuggestionResponse suggestProduct(ProductEnglishSuggestionRequest request) {
        Map<String, String> source = fields(
                "name", request.name(),
                "shortDescription", request.shortDescription(),
                "description", request.description(),
                "material", request.material(),
                "careInstruction", request.careInstruction(),
                "seoTitle", request.seoTitle(),
                "seoDescription", request.seoDescription());
        EnglishContentGenerationResult result = generate(EnglishContentType.PRODUCT, request.model(), source);
        return validate(new ProductEnglishSuggestionResponse(
                TARGET_LOCALE,
                required(result, "name"),
                optional(source, result, "shortDescription"),
                optional(source, result, "description"),
                optional(source, result, "material"),
                optional(source, result, "careInstruction"),
                optional(source, result, "seoTitle"),
                optional(source, result, "seoDescription")));
    }

    @Override
    public CategoryEnglishSuggestionResponse suggestCategory(CategoryEnglishSuggestionRequest request) {
        Map<String, String> source = fields(
                "name", request.name(),
                "description", request.description(),
                "seoTitle", request.seoTitle(),
                "seoDescription", request.seoDescription());
        EnglishContentGenerationResult result = generate(EnglishContentType.CATEGORY, request.model(), source);
        return validate(new CategoryEnglishSuggestionResponse(
                TARGET_LOCALE,
                required(result, "name"),
                optional(source, result, "description"),
                optional(source, result, "seoTitle"),
                optional(source, result, "seoDescription")));
    }

    @Override
    public SaleCampaignEnglishSuggestionResponse suggestSaleCampaign(
            SaleCampaignEnglishSuggestionRequest request) {
        Map<String, String> source = fields(
                "name", request.name(),
                "description", request.description());
        EnglishContentGenerationResult result = generate(EnglishContentType.SALE_CAMPAIGN, request.model(), source);
        return validate(new SaleCampaignEnglishSuggestionResponse(
                TARGET_LOCALE,
                required(result, "name"),
                optional(source, result, "description")));
    }

    private EnglishContentGenerationResult generate(
            EnglishContentType contentType,
            String requestedModel,
            Map<String, String> sourceFields) {
        ensureAvailable();
        validateInputSize(sourceFields);
        String model = resolveModel(requestedModel);
        EnglishContentGenerationResult result = provider.generate(
                new EnglishContentGenerationRequest(contentType, model, sourceFields));
        if (result == null || result.fields() == null) {
            throw invalidResponse();
        }
        return result;
    }

    private void ensureAvailable() {
        if (!properties.enabled() || !properties.hasApiKey()) {
            throw new EnglishContentGenerationException(
                    "CONTENT_GENERATION_DISABLED",
                    "English content generation is not configured.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String resolveModel(String requestedModel) {
        boolean explicit = requestedModel != null && !requestedModel.isBlank();
        String model = explicit ? requestedModel.trim() : properties.defaultModel();
        if (properties.supports(model)) {
            return model;
        }
        if (!explicit) {
            throw new EnglishContentGenerationException(
                    "CONTENT_GENERATION_DISABLED",
                    "English content generation is not configured.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        throw new EnglishContentGenerationException(
                "CONTENT_GENERATION_MODEL_NOT_ALLOWED",
                "The requested English content model is not allowed.",
                HttpStatus.BAD_REQUEST,
                Map.of("allowedModels", GeminiEnglishContentProperties.SUPPORTED_MODELS));
    }

    private void validateInputSize(Map<String, String> fields) {
        int totalCharacters = fields.values().stream()
                .filter(value -> value != null)
                .mapToInt(String::length)
                .sum();
        if (totalCharacters > properties.maxInputCharacters()) {
            throw new EnglishContentGenerationException(
                    "CONTENT_GENERATION_INPUT_TOO_LARGE",
                    "Vietnamese source content is too large to generate a suggestion.",
                    HttpStatus.BAD_REQUEST,
                    Map.of("maxInputCharacters", properties.maxInputCharacters()));
        }
    }

    private <T> T validate(T response) {
        Set<ConstraintViolation<T>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            throw invalidResponse();
        }
        return response;
    }

    private String required(EnglishContentGenerationResult result, String field) {
        String value = result.fields().get(field);
        return value == null ? null : value.trim();
    }

    private String optional(
            Map<String, String> sourceFields,
            EnglishContentGenerationResult result,
            String field) {
        String sourceValue = sourceFields.get(field);
        if (sourceValue == null || sourceValue.isBlank()) {
            return null;
        }
        String value = result.fields().get(field);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private EnglishContentGenerationException invalidResponse() {
        return new EnglishContentGenerationException(
                "CONTENT_GENERATION_INVALID_RESPONSE",
                "The English content provider returned an invalid response.",
                HttpStatus.BAD_GATEWAY);
    }

    private Map<String, String> fields(String... pairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            fields.put(pairs[index], pairs[index + 1]);
        }
        return fields;
    }
}
