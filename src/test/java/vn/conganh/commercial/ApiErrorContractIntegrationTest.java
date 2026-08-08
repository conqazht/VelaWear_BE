package vn.conganh.commercial;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;

public class ApiErrorContractIntegrationTest extends AuthenticatedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGenericExceptionReturnsInternalServerErrorWithoutDetails() throws Exception {
        mockMvc.perform(get("/api/v1/products/test-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.message", is("Internal server error")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @TestConfiguration
    static class TestConfig {
        @RestController
        static class TestController {
            @GetMapping("/api/v1/products/test-error")
            public void testError() throws Exception {
                throw new Exception("This is a generic error that should not leak");
            }
        }
    }
}
