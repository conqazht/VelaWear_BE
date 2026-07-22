package vn.conganh.commercial.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldHandleHttpMessageNotReadable() throws Exception {
        mockMvc.perform(post("/dummy/pojo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REQUEST_BODY_INVALID")))
                .andExpect(jsonPath("$.message", is("Malformed JSON or unreadable request body")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldHandleHttpRequestMethodNotSupported() throws Exception {
        mockMvc.perform(post("/dummy/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")))
                .andExpect(jsonPath("$.message", is("Unsupported HTTP method")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldHandleHttpMediaTypeNotSupported() throws Exception {
        mockMvc.perform(post("/dummy/pojo")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<xml></xml>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")))
                .andExpect(jsonPath("$.message", is("Unsupported content type")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldHandleIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/dummy/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", is("Invalid argument provided")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldHandleInvalidDataAccessApiUsageException() throws Exception {
        mockMvc.perform(get("/dummy/invalid-data-access"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", is("Invalid query usage")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldHandleGenericException() throws Exception {
        mockMvc.perform(get("/dummy/generic-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.message", is("Internal server error")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    static class DummyController {
        @PostMapping("/dummy/body")
        public void dummyBody(@RequestBody String body) {
            // Spring throws HttpMessageNotReadableException if content is missing/malformed for @RequestBody
            // Wait, for String it might not throw, let's use a POJO
        }

        @PostMapping("/dummy/pojo")
        public void dummyPojo(@RequestBody DummyDto dto) {
        }

        @GetMapping("/dummy/get-only")
        public void getOnly() {
        }

        @GetMapping("/dummy/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("This is a root cause text that should not leak");
        }

        @GetMapping("/dummy/invalid-data-access")
        public void invalidDataAccess() {
            throw new InvalidDataAccessApiUsageException("This is a data access error that should not leak");
        }

        @GetMapping("/dummy/generic-exception")
        public void genericException() throws Exception {
            throw new Exception("This is a generic error that should not leak");
        }
    }

    static class DummyDto {
        public String name;
    }
}
