package vn.conganh.commercial.feature.catalog.i18n.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.TestDataFactory;

@Transactional
@TestPropertySource(properties = {
        "app.english-content.gemini.enabled=true",
        "app.english-content.gemini.api-key=test-only-key"
})
class EnglishContentSuggestionControllerIntegrationTest extends AuthenticatedIntegrationTest {

    private static final String PRODUCT_PATH = "/api/v1/products/translation-suggestions/en";
    private static final String CATEGORY_PATH = "/api/v1/categories/translation-suggestions/en";
    private static final String SALE_PATH = "/api/v1/sale-campaigns/translation-suggestions/en";

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EnglishContentProvider provider;

    private String allowedToken;
    private String forbiddenToken;

    @BeforeEach
    void setUp() {
        testDataFactory.seedPermissions("PRODUCT", PRODUCT_PATH, "POST");
        testDataFactory.seedPermissions("CATEGORY", CATEGORY_PATH, "POST");
        testDataFactory.seedPermissions("SALE", SALE_PATH, "POST");
        allowedToken = testDataFactory.jwtWithPermission();
        forbiddenToken = testDataFactory.jwtWithoutPermission();

        when(provider.generate(any())).thenAnswer(invocation -> {
            EnglishContentGenerationRequest request = invocation.getArgument(0);
            Map<String, String> fields = new LinkedHashMap<>();
            for (String field : request.contentType().fields()) {
                fields.put(field, field.equals("name") ? "English name" : null);
            }
            return new EnglishContentGenerationResult(fields);
        });
    }

    @AfterEach
    void tearDown() {
        testDataFactory.cleanup();
    }

    @Test
    void allSuggestionEndpoints_returnTypedEnglishContentWithoutPersistingTranslations() throws Exception {
        int productTranslationsBefore = count("product_translations");
        int categoryTranslationsBefore = count("category_translations");
        int saleTranslationsBefore = count("sale_campaign_translations");

        mockMvc.perform(post(PRODUCT_PATH)
                        .header("Authorization", "Bearer " + allowedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"gemini-3.5-flash","name":"Tên sản phẩm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localeCode").value("en"))
                .andExpect(jsonPath("$.data.name").value("English name"))
                .andExpect(jsonPath("$.data.slug").doesNotExist());

        mockMvc.perform(post(CATEGORY_PATH)
                        .header("Authorization", "Bearer " + allowedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"Tên danh mục\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localeCode").value("en"))
                .andExpect(jsonPath("$.data.name").value("English name"))
                .andExpect(jsonPath("$.data.slug").doesNotExist());

        mockMvc.perform(post(SALE_PATH)
                        .header("Authorization", "Bearer " + allowedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"Tên khuyến mãi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localeCode").value("en"))
                .andExpect(jsonPath("$.data.name").value("English name"))
                .andExpect(jsonPath("$.data.slug").doesNotExist());

        assertThat(count("product_translations")).isEqualTo(productTranslationsBefore);
        assertThat(count("category_translations")).isEqualTo(categoryTranslationsBefore);
        assertThat(count("sale_campaign_translations")).isEqualTo(saleTranslationsBefore);
    }

    @Test
    void productSuggestion_requiresAuthenticationAndPermission() throws Exception {
        String body = "{" + "\"name\":\"Tên sản phẩm\"}";

        mockMvc.perform(post(PRODUCT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(PRODUCT_PATH)
                        .header("Authorization", "Bearer " + forbiddenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void productSuggestion_validatesNameAndModelWhitelist() throws Exception {
        mockMvc.perform(post(PRODUCT_PATH)
                        .header("Authorization", "Bearer " + allowedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        mockMvc.perform(post(PRODUCT_PATH)
                        .header("Authorization", "Bearer " + allowedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"gemini-not-allowed","name":"Tên sản phẩm"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_GENERATION_MODEL_NOT_ALLOWED"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
