package vn.conganh.commercial.feature.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;

@Transactional
@DisplayName("Module Category - CategoryController")
class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /categories - 201: tạo category thành công khi dữ liệu hợp lệ")
        void createCategory_validRequest_returnsCreatedCategory() throws Exception {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(
                    null,
                    "Test Category",
                    "test-category-post",
                    1,
                    "ACTIVE");

            // Act & Assert
            mockMvc.perform(post("/api/v1/categories")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("Test Category")))
                    .andExpect(jsonPath("$.data.slug", is("test-category-post")));

            assertThat(categoryRepository.existsBySlug("test-category-post")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /categories - 400: từ chối khi name để trống")
        void createCategory_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(null, "", "blank-category-post", 1, "ACTIVE");

            // Act & Assert
            assertValidationFailure(request, "blank-category-post");
        }

        @Test
        @DisplayName("POST /categories - 400: từ chối khi slug để trống")
        void createCategory_blankSlug_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateCategoryRequest request = new CreateCategoryRequest(null, "Blank Slug Category", "", 1, "ACTIVE");

            // Act & Assert
            mockMvc.perform(post("/api/v1/categories")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(categoryRepository.findAll())
                    .noneMatch(category -> category.getName().equals("Blank Slug Category"));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /categories - 400: từ chối khi slug đã tồn tại")
        void createCategory_duplicateSlug_returnsBadRequestAndDoesNotCreateNewCategory() throws Exception {
            // Arrange
            Category category = new Category();
            category.setName("Existing Category");
            category.setSlug("duplicate-category-post");
            category.setSortOrder(1);
            category.setStatus("ACTIVE");
            categoryRepository.save(category);

            CreateCategoryRequest request = new CreateCategoryRequest(
                    null,
                    "Duplicate Category",
                    "duplicate-category-post",
                    2,
                    "ACTIVE");
            long countBefore = categoryRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/categories")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(categoryRepository.count()).isEqualTo(countBefore);
        }
    }

    private void assertValidationFailure(CreateCategoryRequest request, String slug) throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

        assertThat(categoryRepository.existsBySlug(slug)).isFalse();
    }

    private String adminToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("admin@velawear.local")
                .claim("userId", 1L)
                .claim("roles", java.util.List.of("ROLE_ADMIN"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
