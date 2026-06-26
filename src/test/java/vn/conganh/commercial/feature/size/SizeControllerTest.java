package vn.conganh.commercial.feature.size;

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
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;

@Transactional
@DisplayName("Module Size - SizeController")
class SizeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private SizeRepository sizeRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /sizes - 201: tạo size thành công khi dữ liệu hợp lệ")
        void createSize_validRequest_returnsCreatedSize() throws Exception {
            // Arrange
            CreateSizeRequest request = new CreateSizeRequest("TEST_SIZE", 1);

            // Act & Assert
            mockMvc.perform(post("/api/v1/sizes")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("TEST_SIZE")));

            assertThat(sizeRepository.existsByName("TEST_SIZE")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /sizes - 400: từ chối khi name để trống")
        void createSize_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateSizeRequest request = new CreateSizeRequest("", 1);
            long countBefore = sizeRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/sizes")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(sizeRepository.count()).isEqualTo(countBefore);
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /sizes - 400: từ chối khi name đã tồn tại")
        void createSize_duplicateName_returnsBadRequestAndDoesNotCreateNewSize() throws Exception {
            // Arrange
            Size size = new Size();
            size.setName("DUPLICATE_SIZE");
            size.setSortOrder(1);
            sizeRepository.save(size);

            CreateSizeRequest request = new CreateSizeRequest("DUPLICATE_SIZE", 2);
            long countBefore = sizeRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/sizes")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(sizeRepository.count()).isEqualTo(countBefore);
        }
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
