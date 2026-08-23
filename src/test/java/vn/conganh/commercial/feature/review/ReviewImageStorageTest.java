package vn.conganh.commercial.feature.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.file.ImageFileWriter;

@DisplayName("Module Review - ReviewImageStorage")
class ReviewImageStorageTest {

    @TempDir
    private Path uploadDirectory;

    private ReviewImageStorage reviewImageStorage;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties(
                uploadDirectory.toString(),
                "/uploads",
                10,
                List.of("jpg", "jpeg", "png", "webp"),
                List.of("avatars", "products"));
        reviewImageStorage = new ReviewImageStorage(properties, new ImageFileWriter(properties));
    }

    @Test
    @DisplayName("store - ghi ảnh bằng tên UUID và không để lại file tạm")
    void store_validImages_writesUuidFilesAtomically() throws Exception {
        List<StoredReviewImage> storedImages = reviewImageStorage.store(List.of(
                image("first.jpg", "image/jpeg", jpegBytes()),
                image("second.png", "image/png", pngBytes())));

        assertThat(storedImages).hasSize(2);
        assertThat(storedImages).allSatisfy(stored -> {
            assertThat(stored.url()).matches("/uploads/reviews/[0-9a-f-]+\\.(jpg|png)");
            assertThat(Files.exists(stored.path())).isTrue();
        });
        try (Stream<Path> files = Files.list(uploadDirectory.resolve("reviews"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(fileName -> fileName.endsWith(".tmp"));
        }
    }

    @Test
    @DisplayName("store - từ chối quá năm ảnh")
    void store_moreThanFiveImages_throwsBadRequest() {
        MockMultipartFile image = image("review.jpg", "image/jpeg", jpegBytes());

        assertThatThrownBy(() -> reviewImageStorage.store(List.of(image, image, image, image, image, image)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A review can contain at most 5 images");
    }

    @Test
    @DisplayName("store - từ chối ảnh vượt kích thước, sai MIME hoặc sai signature")
    void store_invalidImage_throwsBadRequest() {
        assertThatThrownBy(() -> reviewImageStorage.store(List.of(
                image("large.jpg", "image/jpeg", new byte[11]))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exceeds");
        assertThatThrownBy(() -> reviewImageStorage.store(List.of(
                image("review.jpg", "image/png", jpegBytes()))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("content type");
        assertThatThrownBy(() -> reviewImageStorage.store(List.of(
                image("review.jpg", "image/jpeg", new byte[] {0x01, 0x02, 0x03}))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("content does not match");
    }

    @Test
    @DisplayName("cleanup - chỉ xóa file cũ không còn được DB tham chiếu")
    void deleteUnreferencedOlderThan_mixedFiles_deletesOnlyOldOrphan() throws Exception {
        Path directory = Files.createDirectories(uploadDirectory.resolve("reviews"));
        Path referenced = Files.write(directory.resolve("referenced.jpg"), jpegBytes());
        Path orphan = Files.write(directory.resolve("orphan.jpg"), jpegBytes());
        Path recent = Files.write(directory.resolve("recent.jpg"), jpegBytes());
        Instant oldTime = Instant.now().minusSeconds(48 * 60 * 60);
        Files.setLastModifiedTime(referenced, FileTime.from(oldTime));
        Files.setLastModifiedTime(orphan, FileTime.from(oldTime));

        int deleted = reviewImageStorage.deleteUnreferencedOlderThan(
                Instant.now().minusSeconds(24 * 60 * 60),
                Set.of("/uploads/reviews/referenced.jpg"));

        assertThat(deleted).isEqualTo(1);
        assertThat(referenced).exists();
        assertThat(orphan).doesNotExist();
        assertThat(recent).exists();
    }

    private MockMultipartFile image(String fileName, String contentType, byte[] bytes) {
        return new MockMultipartFile("images", fileName, contentType, bytes);
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
    }
}
