package vn.conganh.commercial.feature.file;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Testcontainers
@TestPropertySource(properties = {
        "app.upload.base-dir=target/test-uploads/file-controller",
        "app.upload.url-prefix=/uploads",
        "app.upload.max-size-bytes=5242880",
        "app.upload.allowed-extensions=jpg,jpeg,png,webp",
        "app.upload.allowed-folders=avatars,logos"
})
@DisplayName("Module File - FileController")
class FileControllerTest {

    private static final String ENDPOINT = "/api/v1/files";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Nested
    @DisplayName("Upload file")
    class UploadFile {

        @Test
        @DisplayName("POST /api/v1/files - 201: upload anh hop le")
        void upload_validImage_returnsCreatedApiResponse() throws Exception {
            // Arrange
            byte[] imageContent = jpegBytes();

            // Act & Assert
            mockMvc.perform(multipart(ENDPOINT)
                            .file(imageFile("avatar.jpg", "image/jpeg", imageContent))
                            .param("folder", "avatars")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.message").value("Created"))
                    .andExpect(jsonPath("$.data.fileName").exists())
                    .andExpect(jsonPath("$.data.folder").value("avatars"))
                    .andExpect(jsonPath("$.data.fileUrl").value(startsWith("/uploads/avatars/")))
                    .andExpect(jsonPath("$.data.size").value(imageContent.length));
        }

        @Test
        @DisplayName("POST /api/v1/files - 400: tu choi file khong phai anh hop le")
        void upload_invalidImageContent_returnsBadRequestApiResponse() throws Exception {
            // Arrange
            byte[] invalidContent = new byte[] {0x4E, 0x4F, 0x54};

            // Act & Assert
            mockMvc.perform(multipart(ENDPOINT)
                            .file(imageFile("avatar.jpg", "image/jpeg", invalidContent))
                            .param("folder", "avatars")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").value("File content does not match file extension"));
        }

        @Test
        @DisplayName("POST /api/v1/files - 400: tu choi request thieu file")
        void upload_missingFile_returnsBadRequestApiResponse() throws Exception {
            // Act & Assert
            mockMvc.perform(multipart(ENDPOINT)
                            .param("folder", "avatars")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").value("Required upload parameter is missing"));
        }

        @Test
        @DisplayName("POST /api/v1/files - 401: tu choi request khong co access token")
        void upload_missingToken_returnsUnauthorized() throws Exception {
            // Act & Assert
            mockMvc.perform(multipart(ENDPOINT)
                            .file(imageFile("avatar.jpg", "image/jpeg", jpegBytes()))
                            .param("folder", "avatars"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/files - 403: tu choi token khong co quyen upload")
        void upload_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
            // Act & Assert
            mockMvc.perform(multipart(ENDPOINT)
                            .file(imageFile("avatar.jpg", "image/jpeg", jpegBytes()))
                            .param("folder", "avatars")
                            .header("Authorization", "Bearer " + noAccessToken()))
                    .andExpect(status().isForbidden());
        }
    }

    private MockMultipartFile imageFile(String originalFileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", originalFileName, contentType, content);
    }

    private String adminToken() {
        return tokenWithRoles("admin@velawear.local", 1L, List.of("ROLE_ADMIN"));
    }

    private String noAccessToken() {
        return tokenWithRoles("no-access@velawear.local", 999_999L, List.of("ROLE_NO_ACCESS"));
    }

    private String tokenWithRoles(String email, Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
