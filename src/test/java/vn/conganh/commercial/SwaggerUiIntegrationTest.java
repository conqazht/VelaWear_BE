package vn.conganh.commercial;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("System/Swagger - Swagger UI auth helper")
class SwaggerUiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /swagger-ui/swagger-initializer.js - trả về script tự lưu access token sau login/refresh")
    void swaggerInitializer_existingResource_returnsAutoAuthorizeScript() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("velawear.swagger.accessToken")))
                .andExpect(content().string(containsString("/api/v1/auth/login")))
                .andExpect(content().string(containsString("/api/v1/auth/refresh")));
    }
}
