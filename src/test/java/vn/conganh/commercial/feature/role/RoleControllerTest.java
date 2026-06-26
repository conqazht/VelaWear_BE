package vn.conganh.commercial.feature.role;

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
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;

@Transactional
@DisplayName("Module Role - RoleController")
class RoleControllerTest extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /roles - 201: tạo role thành công khi dữ liệu hợp lệ")
        void createRole_validRequest_returnsCreatedRole() throws Exception {
            // Arrange
            CreateRoleRequest request = new CreateRoleRequest("TEST_ROLE_POST", "Role integration test");

            // Act & Assert
            mockMvc.perform(post("/api/v1/roles")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("TEST_ROLE_POST")))
                    .andExpect(jsonPath("$.data.description", is("Role integration test")));

            assertThat(roleRepository.existsByName("TEST_ROLE_POST")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /roles - 400: từ chối khi name để trống")
        void createRole_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateRoleRequest request = new CreateRoleRequest("", "Blank role");
            long countBefore = roleRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/roles")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(roleRepository.count()).isEqualTo(countBefore);
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /roles - 400: từ chối khi role name đã tồn tại")
        void createRole_duplicateName_returnsBadRequestAndDoesNotCreateNewRole() throws Exception {
            // Arrange
            Role role = new Role();
            role.setName("DUPLICATE_ROLE_POST");
            role.setDescription("Existing role");
            roleRepository.save(role);

            CreateRoleRequest request = new CreateRoleRequest("DUPLICATE_ROLE_POST", "Duplicate role");
            long countBefore = roleRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/roles")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(roleRepository.count()).isEqualTo(countBefore);
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
