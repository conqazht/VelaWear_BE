package vn.conganh.commercial.feature.color;

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
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;

@Transactional
@DisplayName("Module Color - ColorController")
class ColorControllerTest extends AbstractIntegrationTest {

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /colors - 201: tạo color thành công khi dữ liệu hợp lệ")
        void createColor_validRequest_returnsCreatedColor() throws Exception {
            // Arrange
            CreateColorRequest request = new CreateColorRequest("Test Color", "#123456", 1);

            // Act & Assert
            mockMvc.perform(post("/api/v1/colors")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("Test Color")))
                    .andExpect(jsonPath("$.data.hexCode", is("#123456")));

            assertThat(colorRepository.existsByName("Test Color")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /colors - 400: từ chối khi name để trống")
        void createColor_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateColorRequest request = new CreateColorRequest("", "#123456", 1);

            // Act & Assert
            mockMvc.perform(post("/api/v1/colors")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(colorRepository.findAll()).noneMatch(color -> color.getHexCode().equals("#123456"));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /colors - 400: từ chối khi name đã tồn tại")
        void createColor_duplicateName_returnsBadRequestAndDoesNotCreateNewColor() throws Exception {
            // Arrange
            Color color = new Color();
            color.setName("Duplicate Color");
            color.setHexCode("#000000");
            color.setSortOrder(1);
            colorRepository.save(color);

            CreateColorRequest request = new CreateColorRequest("Duplicate Color", "#ffffff", 2);
            long countBefore = colorRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/colors")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(colorRepository.count()).isEqualTo(countBefore);
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
