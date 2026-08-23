package vn.conganh.commercial.feature.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;

@DisplayName("ImageFileWriter component")
class ImageFileWriterTest {

    @TempDir
    private Path tempDirectory;

    private ImageFileWriter imageFileWriter;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties(
                tempDirectory.toString(),
                "/uploads",
                100,
                List.of("jpg", "jpeg", "png", "webp"),
                List.of("avatars", "reviews", "products"));
        imageFileWriter = new ImageFileWriter(properties);
    }

    @Test
    @DisplayName("writeWithSanitizedName stores file with sanitized name and UUID prefix")
    void writeWithSanitizedName_validFile_storesSuccessfully() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "my avatar (1).jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        String fileName = imageFileWriter.writeWithSanitizedName(file, tempDirectory);

        assertThat(fileName).matches("[0-9a-f-]{36}_my_avatar_1_\\.jpg");
        assertThat(Files.exists(tempDirectory.resolve(fileName))).isTrue();
    }

    @Test
    @DisplayName("writeWithUuidName stores file with pure UUID filename")
    void writeWithUuidName_validFile_storesSuccessfully() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "review.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        String fileName = imageFileWriter.writeWithUuidName(file, tempDirectory);

        assertThat(fileName).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(tempDirectory.resolve(fileName))).isTrue();
    }

    @Test
    @DisplayName("Throws InvalidRequestException when file content doesn't match extension")
    void write_invalidSignature_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                new byte[] {0x01, 0x02, 0x03});

        assertThatThrownBy(() -> imageFileWriter.writeWithUuidName(file, tempDirectory))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("File content does not match file extension");
    }

    @Test
    @DisplayName("Throws InvalidRequestException on path traversal filename")
    void write_pathTraversal_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../secret.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> imageFileWriter.writeWithUuidName(file, tempDirectory))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid file name");
    }
}
