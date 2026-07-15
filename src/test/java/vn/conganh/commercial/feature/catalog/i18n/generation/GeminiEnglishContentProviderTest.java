package vn.conganh.commercial.feature.catalog.i18n.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.config.GeminiEnglishContentProperties;
import vn.conganh.commercial.exception.EnglishContentGenerationException;

class GeminiEnglishContentProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiEnglishContentProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiEnglishContentProperties properties = properties();
        provider = new GeminiEnglishContentProvider(
                builder.baseUrl(properties.baseUrl()).build(), objectMapper, properties);
    }

    @Test
    void generate_sendsApiKeyAndLiveValidatedStructuredOutputShape() throws Exception {
        Map<String, String> generated = new LinkedHashMap<>();
        generated.put("name", "English sale");
        generated.put("description", "English description");
        String providerResponse = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "finishReason", "STOP",
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", objectMapper.writeValueAsString(generated))))))));

        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andExpect(jsonPath("$.generationConfig.responseFormat").doesNotExist())
                .andExpect(jsonPath("$.contents[0].parts[0].text").exists())
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        EnglishContentGenerationResult result = provider.generate(new EnglishContentGenerationRequest(
                EnglishContentType.SALE_CAMPAIGN,
                "gemini-3.5-flash",
                Map.of("name", "Khuyến mãi", "description", "Mô tả")));

        assertThat(result.fields())
                .containsEntry("name", "English sale")
                .containsEntry("description", "English description")
                .doesNotContainKey("slug");
        server.verify();
    }

    @Test
    void generate_mapsProvider429ToCoded429() {
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> {
                    EnglishContentGenerationException typed = (EnglishContentGenerationException) exception;
                    assertThat(typed.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(typed.getCode()).isEqualTo("CONTENT_GENERATION_RATE_LIMITED");
                });
    }

    @Test
    void generate_mapsProviderFailureToBadGatewayWithoutLeakingBody() {
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("provider-secret-diagnostic"));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> {
                    EnglishContentGenerationException typed = (EnglishContentGenerationException) exception;
                    assertThat(typed.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(typed.getCode()).isEqualTo("CONTENT_GENERATION_PROVIDER_ERROR");
                    assertThat(typed.getMessage()).doesNotContain("provider-secret-diagnostic");
                });
    }

    @Test
    void generate_rejectsMalformedGeneratedJson() throws Exception {
        String providerResponse = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of("parts", List.of(Map.of("text", "not-json")))))));
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> {
                    EnglishContentGenerationException typed = (EnglishContentGenerationException) exception;
                    assertThat(typed.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(typed.getCode()).isEqualTo("CONTENT_GENERATION_INVALID_RESPONSE");
                });
    }

    @Test
    void generate_rejectsCandidateStoppedByMaxTokensEvenWhenJsonIsParseable() throws Exception {
        Map<String, String> generated = new LinkedHashMap<>();
        generated.put("name", "English sale");
        generated.put("description", "Truncated but parseable");
        String providerResponse = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "finishReason", "MAX_TOKENS",
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", objectMapper.writeValueAsString(generated))))))));
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(request()))
                .isInstanceOf(EnglishContentGenerationException.class)
                .satisfies(exception -> {
                    EnglishContentGenerationException typed = (EnglishContentGenerationException) exception;
                    assertThat(typed.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(typed.getCode()).isEqualTo("CONTENT_GENERATION_INVALID_RESPONSE");
                });
    }

    private EnglishContentGenerationRequest request() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("name", "Khuyến mãi");
        source.put("description", null);
        return new EnglishContentGenerationRequest(
                EnglishContentType.SALE_CAMPAIGN,
                "gemini-3.1-flash-lite",
                source);
    }

    private GeminiEnglishContentProperties properties() {
        return new GeminiEnglishContentProperties(
                true,
                "test-gemini-key",
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-3.1-flash-lite",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                30_000,
                16_384);
    }
}
