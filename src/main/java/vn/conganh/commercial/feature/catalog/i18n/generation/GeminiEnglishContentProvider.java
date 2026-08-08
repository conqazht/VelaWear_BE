package vn.conganh.commercial.feature.catalog.i18n.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.config.GeminiEnglishContentProperties;
import vn.conganh.commercial.exception.EnglishContentGenerationException;

@Slf4j
@Component
public class GeminiEnglishContentProvider implements EnglishContentProvider {

    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final String SYSTEM_INSTRUCTION = """
            You translate Vietnamese ecommerce catalog content into clear, natural English.
            Translate faithfully and preserve product names, brand names, measurements, materials,
            formatting and every factual claim. Never invent benefits, specifications, discounts or
            campaign conditions. A null source field must remain null. Return only the structured
            JSON requested by the response schema.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiEnglishContentProperties properties;

    public GeminiEnglishContentProvider(
            @Qualifier("geminiEnglishContentRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            GeminiEnglishContentProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public EnglishContentGenerationResult generate(EnglishContentGenerationRequest request) {
        try {
            String rawResponse = restClient.post()
                    .uri("/models/{model}:generateContent", request.model())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(request))
                    .retrieve()
                    .body(String.class);
            return parseResponse(rawResponse, request.contentType());
        } catch (EnglishContentGenerationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Gemini content generation returned HTTP {} for model {}", status, request.model());
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw error(
                        "CONTENT_GENERATION_RATE_LIMITED",
                        "The English content provider rate limit was reached. Please try again later.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            throw providerError();
        } catch (ResourceAccessException exception) {
            log.warn("Gemini content generation timed out or was unreachable for model {}", request.model());
            throw providerError();
        } catch (JacksonException exception) {
            log.warn("Gemini content generation returned malformed JSON for model {}", request.model());
            throw invalidResponse();
        } catch (RestClientException exception) {
            log.warn("Gemini content generation failed with {} for model {}",
                    exception.getClass().getSimpleName(), request.model());
            throw providerError();
        }
    }

    private Map<String, Object> buildBody(EnglishContentGenerationRequest request) throws JacksonException {
        Map<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("contentType", request.contentType().name());
        promptPayload.put("sourceLocale", "vi");
        promptPayload.put("targetLocale", "en");
        promptPayload.put("fields", request.sourceFields());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", objectMapper.writeValueAsString(promptPayload))))));
        body.put("generationConfig", Map.of(
                "maxOutputTokens", properties.maxOutputTokens(),
                "responseMimeType", "application/json",
                "responseJsonSchema", schema(request.contentType())));
        return body;
    }

    private Map<String, Object> schema(EnglishContentType contentType) {
        Map<String, Object> schemaProperties = new LinkedHashMap<>();
        for (String field : contentType.fields()) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", field.equals("name") ? "string" : List.of("string", "null"));
            definition.put("description", field.equals("name")
                    ? "Faithful English translation of the Vietnamese name."
                    : "Faithful English translation; return null when the source field is null or blank.");
            schemaProperties.put(field, definition);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", schemaProperties);
        schema.put("required", contentType.fields());
        schema.put("additionalProperties", false);
        return schema;
    }

    private EnglishContentGenerationResult parseResponse(
            String rawResponse,
            EnglishContentType contentType) throws JacksonException {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw invalidResponse();
        }

        JsonNode response = objectMapper.readTree(rawResponse);
        JsonNode candidates = response.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            throw invalidResponse();
        }

        JsonNode candidate = candidates.get(0);
        JsonNode finishReason = candidate.get("finishReason");
        if (finishReason != null
                && (!finishReason.isTextual() || !"STOP".equals(finishReason.asString()))) {
            throw invalidResponse();
        }

        JsonNode content = candidate.get("content");
        JsonNode parts = content == null ? null : content.get("parts");
        if (parts == null || !parts.isArray()) {
            throw invalidResponse();
        }

        String generatedJson = null;
        for (int index = 0; index < parts.size(); index++) {
            JsonNode text = parts.get(index).get("text");
            if (text != null && text.isTextual() && !text.asString().isBlank()) {
                generatedJson = text.asString();
                break;
            }
        }
        if (generatedJson == null) {
            throw invalidResponse();
        }

        JsonNode generated = objectMapper.readTree(generatedJson);
        if (generated == null || !generated.isObject()
                || generated.size() != contentType.fields().size()) {
            throw invalidResponse();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (String field : contentType.fields()) {
            JsonNode value = generated.get(field);
            if (value == null || (!value.isNull() && !value.isTextual())) {
                throw invalidResponse();
            }
            fields.put(field, value.isNull() ? null : value.asString());
        }
        return new EnglishContentGenerationResult(fields);
    }

    private EnglishContentGenerationException providerError() {
        return error(
                "CONTENT_GENERATION_PROVIDER_ERROR",
                "The English content provider is currently unavailable.",
                HttpStatus.BAD_GATEWAY);
    }

    private EnglishContentGenerationException invalidResponse() {
        return error(
                "CONTENT_GENERATION_INVALID_RESPONSE",
                "The English content provider returned an invalid response.",
                HttpStatus.BAD_GATEWAY);
    }

    private EnglishContentGenerationException error(String code, String message, HttpStatus status) {
        return new EnglishContentGenerationException(code, message, status);
    }
}
