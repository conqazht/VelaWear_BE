package vn.conganh.commercial.feature.brand;

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
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;

@Transactional
@DisplayName("Module Brand - BrandController")
class BrandControllerTest extends AbstractIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /brands - 201: tạo brand thành công khi dữ liệu hợp lệ")
        void createBrand_validRequest_returnsCreatedBrand() throws Exception {
            // Arrange
            CreateBrandRequest request = new CreateBrandRequest(
                    "Test Brand",
                    "test-brand-post",
                    "Brand integration test",
                    "ACTIVE");

            // Act & Assert
            mockMvc.perform(post("/api/v1/brands")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("Test Brand")))
                    .andExpect(jsonPath("$.data.slug", is("test-brand-post")));

            assertThat(brandRepository.existsBySlug("test-brand-post")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /brands - 400: từ chối khi name để trống")
        void createBrand_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateBrandRequest request = new CreateBrandRequest("", "blank-brand-post", null, "ACTIVE");

            // Act & Assert
            assertValidationFailure(request, "blank-brand-post");
        }

        @Test
        @DisplayName("POST /brands - 400: từ chối khi slug để trống")
        void createBrand_blankSlug_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateBrandRequest request = new CreateBrandRequest("Blank Slug Brand", "", null, "ACTIVE");

            // Act & Assert
            mockMvc.perform(post("/api/v1/brands")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(brandRepository.findAll()).noneMatch(brand -> brand.getName().equals("Blank Slug Brand"));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /brands - 400: từ chối khi slug đã tồn tại")
        void createBrand_duplicateSlug_returnsBadRequestAndDoesNotCreateNewBrand() throws Exception {
            // Arrange
            Brand brand = new Brand();
            brand.setName("Existing Brand");
            brand.setSlug("duplicate-brand-post");
            brand.setStatus("ACTIVE");
            brandRepository.save(brand);

            CreateBrandRequest request = new CreateBrandRequest(
                    "Duplicate Brand",
                    "duplicate-brand-post",
                    null,
                    "ACTIVE");
            long countBefore = brandRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/brands")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(brandRepository.count()).isEqualTo(countBefore);
        }
    }

    private void assertValidationFailure(CreateBrandRequest request, String slug) throws Exception {
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

        assertThat(brandRepository.existsBySlug(slug)).isFalse();
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
