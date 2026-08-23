package vn.conganh.commercial.feature.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;

/**
 * Shared image validation and atomic file writing engine.
 * Eliminates duplicate file verification (magic bytes, MIME type, size, extension)
 * and atomic .tmp-to-target writes between feature/file and feature/review.
 */
@Component
@RequiredArgsConstructor
public class ImageFileWriter {

    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final String FILE_NAME_SEPARATOR = "_";
    private static final String PATH_TRAVERSAL_TOKEN = "..";
    private static final String URL_SEPARATOR = "/";
    private static final int MAX_SIGNATURE_BYTES = 12;
    private static final int WEBP_FORMAT_OFFSET = 8;

    private final UploadProperties uploadProperties;

    public String writeWithSanitizedName(MultipartFile file, Path targetDirectory) {
        validateFile(file);
        validateSize(file);
        String originalFileName = getOriginalFileName(file);
        String extension = getExtension(originalFileName);
        validateExtension(extension);
        validateImageContent(file, extension);

        String storedFileName = buildSanitizedStoredFileName(originalFileName);
        Path target = buildTargetPath(targetDirectory, storedFileName);
        writeFile(file, target);
        return storedFileName;
    }

    public String writeWithUuidName(MultipartFile file, Path targetDirectory) {
        validateFile(file);
        validateSize(file);
        String originalFileName = getOriginalFileName(file);
        String extension = getExtension(originalFileName);
        validateExtension(extension);
        validateImageContent(file, extension);

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path target = buildTargetPath(targetDirectory, storedFileName);
        writeFile(file, target);
        return storedFileName;
    }

    public void writeFile(MultipartFile file, Path target) {
        Path temporaryTarget = target.resolveSibling(target.getFileName() + ".tmp").normalize();
        if (!temporaryTarget.startsWith(target.getParent())) {
            throw new InvalidRequestException("Invalid file path");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, temporaryTarget, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporaryTarget, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temporaryTarget, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteIfExists(temporaryTarget);
            throw new IllegalStateException("Could not store file", exception);
        }
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File is required");
        }
    }

    public void validateSize(MultipartFile file) {
        if (file.getSize() > uploadProperties.maxSizeBytes()) {
            throw new InvalidRequestException("File size exceeds maximum allowed size");
        }
    }

    public String getOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidRequestException("Original file name is required");
        }
        if (originalFileName.contains(PATH_TRAVERSAL_TOKEN)
                || originalFileName.contains(URL_SEPARATOR)
                || originalFileName.contains("\\")) {
            throw new InvalidRequestException("Invalid file name");
        }
        return originalFileName.trim();
    }

    public String getExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
            throw new InvalidRequestException("File extension is required");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    public void validateExtension(String extension) {
        if (!uploadProperties.allowedExtensions().contains(extension)) {
            throw new InvalidRequestException("File extension is not allowed");
        }
    }

    public void validateImageContent(MultipartFile file, String extension) {
        validateContentType(file, extension);
        byte[] signature = readSignature(file);
        boolean validSignature = switch (extension) {
            case "jpg", "jpeg" -> hasPrefix(signature, 0xFF, 0xD8, 0xFF);
            case "png" -> hasPrefix(signature, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "webp" -> hasPrefix(signature, 0x52, 0x49, 0x46, 0x46)
                    && hasBytesAt(signature, WEBP_FORMAT_OFFSET, 0x57, 0x45, 0x42, 0x50);
            default -> false;
        };

        if (!validSignature) {
            throw new InvalidRequestException("File content does not match file extension");
        }
    }

    private void validateContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()
                || !contentType.equals(expectedContentType(extension))) {
            throw new InvalidRequestException("File content type does not match file extension");
        }
    }

    private String expectedContentType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "";
        };
    }

    private byte[] readSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(MAX_SIGNATURE_BYTES);
        } catch (IOException exception) {
            throw new InvalidRequestException("Could not read file content");
        }
    }

    private boolean hasPrefix(byte[] signature, int... expected) {
        return hasBytesAt(signature, 0, expected);
    }

    private boolean hasBytesAt(byte[] signature, int offset, int... expected) {
        if (signature.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(signature[offset + index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private String buildSanitizedStoredFileName(String originalFileName) {
        String sanitizedFileName = UNSAFE_FILE_NAME_CHARS.matcher(originalFileName)
                .replaceAll(FILE_NAME_SEPARATOR);
        if (sanitizedFileName.isBlank()) {
            throw new InvalidRequestException("Invalid file name");
        }
        return UUID.randomUUID() + FILE_NAME_SEPARATOR + sanitizedFileName;
    }

    private Path buildTargetPath(Path directory, String fileName) {
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new InvalidRequestException("Invalid file path");
        }
        return target;
    }

    private boolean deleteIfExists(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return false;
        }
    }
}
