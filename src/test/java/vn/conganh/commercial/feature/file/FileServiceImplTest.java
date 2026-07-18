package vn.conganh.commercial.feature.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.file.dto.FileUploadResponse;

@DisplayName("Module File - FileServiceImpl")
class FileServiceImplTest {

    @TempDir
    private Path uploadDirectory;

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(uploadProperties(10));
    }

    @Nested
    @DisplayName("Store file")
    class StoreFile {

        @Test
        @DisplayName("store - luu file thanh cong khi du lieu hop le")
        void store_validImage_returnsUploadResponseAndWritesFile() {
            // Arrange
            MockMultipartFile file = imageFile("my photo (2024).jpg", jpegBytes());

            // Act
            FileUploadResponse response = fileService.store(file, "avatars");

            // Assert
            assertThat(response.fileName()).matches("[0-9a-f-]{36}_my_photo_2024_\\.jpg");
            assertThat(response.folder()).isEqualTo("avatars");
            assertThat(response.fileUrl()).isEqualTo("/uploads/avatars/" + response.fileName());
            assertThat(response.size()).isEqualTo(file.getSize());
            assertThat(response.uploadedAt()).isNotNull();
            assertThat(Files.exists(uploadDirectory.resolve("avatars").resolve(response.fileName()))).isTrue();
        }

        @Test
        @DisplayName("store - nem InvalidRequestException khi folder khong duoc phep")
        void store_disallowedFolder_throwsInvalidRequestException() {
            // Arrange
            MockMultipartFile file = imageFile("avatar.jpg", jpegBytes());

            // Act & Assert
            assertThatThrownBy(() -> fileService.store(file, "documents"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Folder is not allowed");
        }

        @Test
        @DisplayName("store - không cho upload review qua API file dùng chung")
        void store_reviewsFolder_throwsInvalidRequestException() {
            MockMultipartFile file = imageFile("review.jpg", jpegBytes());

            assertThatThrownBy(() -> fileService.store(file, "reviews"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Review images must be uploaded through the review endpoint");
        }

        @Test
        @DisplayName("store - nem InvalidRequestException khi extension khong duoc phep")
        void store_disallowedExtension_throwsInvalidRequestException() {
            // Arrange
            MockMultipartFile file = imageFile("script.jsp", jpegBytes());

            // Act & Assert
            assertThatThrownBy(() -> fileService.store(file, "avatars"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("File extension is not allowed");
        }

        @Test
        @DisplayName("store - nem InvalidRequestException khi file vuot qua gioi han")
        void store_oversizedFile_throwsInvalidRequestException() {
            // Arrange
            FileServiceImpl smallLimitService = new FileServiceImpl(uploadProperties(3));
            MockMultipartFile file = imageFile("avatar.jpg", jpegBytes());

            // Act & Assert
            assertThatThrownBy(() -> smallLimitService.store(file, "avatars"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("File size exceeds maximum allowed size");
        }

        @Test
        @DisplayName("store - nem InvalidRequestException khi content-type khong khop extension")
        void store_mismatchedContentType_throwsInvalidRequestException() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "avatar.jpg",
                    "image/png",
                    jpegBytes());

            // Act & Assert
            assertThatThrownBy(() -> fileService.store(file, "avatars"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("File content type does not match file extension");
        }

        @Test
        @DisplayName("store - nem InvalidRequestException khi noi dung file khong phai anh hop le")
        void store_invalidImageContent_throwsInvalidRequestException() {
            // Arrange
            MockMultipartFile file = imageFile("avatar.jpg", new byte[] {0x4E, 0x4F, 0x54});

            // Act & Assert
            assertThatThrownBy(() -> fileService.store(file, "avatars"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("File content does not match file extension");
        }

        @Test
        @DisplayName("store - ghi file qua temporary path và không để lại file tmp")
        void store_validImage_doesNotLeaveTemporaryFile() throws Exception {
            FileUploadResponse response = fileService.storeAvatar(imageFile("avatar.jpg", jpegBytes()));

            assertThat(Files.exists(uploadDirectory.resolve("avatars").resolve(response.fileName()))).isTrue();
            try (var files = Files.list(uploadDirectory.resolve("avatars"))) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .noneMatch(fileName -> fileName.endsWith(".tmp"));
            }
        }
    }

    @Nested
    @DisplayName("Managed avatar delete")
    class ManagedAvatarDelete {

        @Test
        @DisplayName("deleteManagedAvatar - chỉ xóa file trong namespace avatars")
        void deleteManagedAvatar_managedAvatar_deletesOnlyAvatarNamespace() throws Exception {
            Path avatarDirectory = Files.createDirectories(uploadDirectory.resolve("avatars"));
            Path productDirectory = Files.createDirectories(uploadDirectory.resolve("products"));
            Path reviewDirectory = Files.createDirectories(uploadDirectory.resolve("reviews"));
            Path avatar = Files.write(avatarDirectory.resolve("old.jpg"), jpegBytes());
            Path product = Files.write(productDirectory.resolve("product.jpg"), jpegBytes());
            Path review = Files.write(reviewDirectory.resolve("review.jpg"), jpegBytes());

            assertThat(fileService.deleteManagedAvatar("/uploads/avatars/old.jpg")).isTrue();
            assertThat(fileService.deleteManagedAvatar("/uploads/products/product.jpg")).isFalse();
            assertThat(fileService.deleteManagedAvatar("/uploads/reviews/review.jpg")).isFalse();
            assertThat(fileService.deleteManagedAvatar("https://cdn.test.local/avatar.jpg")).isFalse();

            assertThat(avatar).doesNotExist();
            assertThat(product).exists();
            assertThat(review).exists();
        }

        @Test
        @DisplayName("deleteManagedAvatar - từ chối traversal path")
        void deleteManagedAvatar_traversalPath_doesNotDelete() throws Exception {
            Path avatarDirectory = Files.createDirectories(uploadDirectory.resolve("avatars"));
            Path avatar = Files.write(avatarDirectory.resolve("safe.jpg"), jpegBytes());

            assertThat(fileService.deleteManagedAvatar("/uploads/avatars/../safe.jpg")).isFalse();

            assertThat(avatar).exists();
        }
    }

    private UploadProperties uploadProperties(long maxSizeBytes) {
        return new UploadProperties(
                uploadDirectory.toString(),
                "/uploads",
                maxSizeBytes,
                List.of("jpg", "png"),
                List.of("avatars", "products", "reviews"));
    }

    private MockMultipartFile imageFile(String originalFileName, byte[] content) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                contentType(originalFileName),
                content);
    }

    private String contentType(String originalFileName) {
        if (originalFileName.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
