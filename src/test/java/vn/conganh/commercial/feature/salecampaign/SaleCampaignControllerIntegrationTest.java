package vn.conganh.commercial.feature.salecampaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.TestDataFactory;

@Transactional
class SaleCampaignControllerIntegrationTest extends AuthenticatedIntegrationTest {

    private static final String BASE_PATH = "/api/v1/sale-campaigns";

    @Autowired TestDataFactory testDataFactory;
    @Autowired JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() {
        testDataFactory.seedPermissions("SALE", BASE_PATH, "GET");
        testDataFactory.seedPermissions("SALE", BASE_PATH + "/{id}", "GET");
        testDataFactory.seedPermissions("SALE", BASE_PATH + "/{id}/translations", "GET", "PUT");
        adminToken = testDataFactory.jwtWithPermission();
    }

    @AfterEach
    void tearDown() {
        testDataFactory.cleanup();
    }

    @Override
    protected String adminToken() {
        return adminToken;
    }

    @Test
    void rawTranslations_requireRbac_andAdminListAndDetailHonorLocale() throws Exception {
        String code = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long id = jdbcTemplate.queryForObject("""
                insert into sale_campaigns (
                    code, name, description, type, status, starts_at, ends_at, version)
                values (?, 'Khuyến mãi', 'Mô tả', 'STANDARD', 'DRAFT',
                    now() + interval '1 day', now() + interval '2 days', 0)
                returning id
                """, Long.class, code);

        mockMvc.perform(get(BASE_PATH + "/" + id + "/translations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(BASE_PATH + "/" + id + "/translations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"translations":[{
                                  "localeCode":"en",
                                  "name":"English sale",
                                  "description":"English description"
                                }]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.translations[0].localeCode").value("en"));

        mockMvc.perform(get(BASE_PATH + "/" + id + "?locale=en")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("English sale"));

        mockMvc.perform(get(BASE_PATH + "?search=" + code + "&locale=en")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result[0].name").value("English sale"));
    }
}
