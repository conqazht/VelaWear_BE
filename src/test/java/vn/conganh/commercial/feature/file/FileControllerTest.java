package vn.conganh.commercial.feature.file;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;

@Transactional
@TestPropertySource(properties = {
        "app.upload.base-dir=target/test-uploads/file-controller",
        "app.upload.url-prefix=/uploads",
        "app.upload.max-size-bytes=5242880",
        "app.upload.allowed-extensions=jpg,jpeg,png,webp",
        "app.upload.allowed-folders=avatars,logos"
})
@DisplayName("Module File - FileController")
class FileControllerTest extends AuthenticatedIntegrationTest {

    private static final String ENDPOINT = "/api/v1/files";

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

    private String noAccessToken() {
        return tokenWithRoles("no-access@velawear.local", 999_999L, List.of("ROLE_NO_ACCESS"));
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
