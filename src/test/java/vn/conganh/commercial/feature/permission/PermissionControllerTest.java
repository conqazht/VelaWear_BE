package vn.conganh.commercial.feature.permission;

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
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;

@Transactional
@DisplayName("Module Permission - PermissionController")
class PermissionControllerTest extends AbstractIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /permissions - 201: tạo permission thành công khi dữ liệu hợp lệ")
        void createPermission_validRequest_returnsCreatedPermission() throws Exception {
            // Arrange
            CreatePermissionRequest request = new CreatePermissionRequest(
                    "CREATE_TEST_PERMISSION",
                    "/api/v1/test-permissions",
                    "POST",
                    "TEST");

            // Act & Assert
            mockMvc.perform(post("/api/v1/permissions")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("CREATE_TEST_PERMISSION")))
                    .andExpect(jsonPath("$.data.apiPath", is("/api/v1/test-permissions")))
                    .andExpect(jsonPath("$.data.method", is("POST")));

            assertThat(permissionRepository.existsByApiPathAndMethod("/api/v1/test-permissions", "POST")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /permissions - 400: từ chối khi apiPath để trống")
        void createPermission_blankApiPath_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreatePermissionRequest request = new CreatePermissionRequest(
                    "BLANK_PATH_PERMISSION",
                    "",
                    "POST",
                    "TEST");
            long countBefore = permissionRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/permissions")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(permissionRepository.count()).isEqualTo(countBefore);
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /permissions - 400: từ chối khi apiPath và method đã tồn tại")
        void createPermission_duplicatePathAndMethod_returnsBadRequestAndDoesNotCreateNewPermission() throws Exception {
            // Arrange
            Permission permission = new Permission();
            permission.setName("EXISTING_TEST_PERMISSION");
            permission.setApiPath("/api/v1/existing-test-permissions");
            permission.setMethod("POST");
            permission.setModule("TEST");
            permissionRepository.save(permission);

            CreatePermissionRequest request = new CreatePermissionRequest(
                    "DUPLICATE_TEST_PERMISSION",
                    "/api/v1/existing-test-permissions",
                    "POST",
                    "TEST");
            long countBefore = permissionRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/permissions")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(permissionRepository.count()).isEqualTo(countBefore);
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
