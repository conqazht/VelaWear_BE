package vn.conganh.commercial.feature.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.file.dto.FileUploadResponse;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final String FILE_NAME_SEPARATOR = "_";
    private static final String URL_SEPARATOR = "/";
    private static final String PATH_TRAVERSAL_TOKEN = "..";
    private static final int MAX_SIGNATURE_BYTES = 12;
    private static final int WEBP_FORMAT_OFFSET = 8;
    static final String AVATAR_FOLDER = "avatars";
    private static final String RESERVED_REVIEW_FOLDER = "reviews";

    private final UploadProperties uploadProperties;

    @Override
    public FileUploadResponse store(MultipartFile file, String folder) {
        validateFile(file);
        validateSize(file);

        String normalizedFolder = normalizeFolder(folder);
        String originalFileName = getOriginalFileName(file);
        String extension = getExtension(originalFileName);
        validateExtension(extension);
        validateImageContent(file, extension);

        String storedFileName = buildStoredFileName(originalFileName);
        Path target = buildTargetPath(normalizedFolder, storedFileName);
        writeFile(file, target);

        return new FileUploadResponse(
                storedFileName,
                normalizedFolder,
                buildFileUrl(normalizedFolder, storedFileName),
                file.getSize(),
                Instant.now());
    }

    @Override
    public FileUploadResponse storeAvatar(MultipartFile file) {
        return store(file, AVATAR_FOLDER);
    }

    @Override
    public boolean deleteManagedAvatar(String avatarUrl) {
        return managedAvatarPath(avatarUrl)
                .map(this::deleteIfExists)
                .orElse(false);
    }

    List<String> managedAvatarUrlsOlderThan(Instant cutoff) {
        Path directory = folderDirectory(AVATAR_FOLDER);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> isOldRegularFile(path, cutoff))
                    .map(path -> buildFileUrl(AVATAR_FOLDER, path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan avatar upload directory", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File is required");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > uploadProperties.maxSizeBytes()) {
            throw new InvalidRequestException("File size exceeds maximum allowed size");
        }
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            throw new InvalidRequestException("Folder is required");
        }

        String normalizedFolder = folder.trim().toLowerCase(Locale.ROOT);
        if (RESERVED_REVIEW_FOLDER.equals(normalizedFolder)) {
            throw new InvalidRequestException("Review images must be uploaded through the review endpoint");
        }
        if (!uploadProperties.allowedFolders().contains(normalizedFolder)) {
            throw new InvalidRequestException("Folder is not allowed");
        }
        return normalizedFolder;
    }

    private String getOriginalFileName(MultipartFile file) {
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

    private String getExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
            throw new InvalidRequestException("File extension is required");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        if (!uploadProperties.allowedExtensions().contains(extension)) {
            throw new InvalidRequestException("File extension is not allowed");
        }
    }

    private void validateImageContent(MultipartFile file, String extension) {
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

    private String buildStoredFileName(String originalFileName) {
        String sanitizedFileName = UNSAFE_FILE_NAME_CHARS.matcher(originalFileName)
                .replaceAll(FILE_NAME_SEPARATOR);
        if (sanitizedFileName.isBlank()) {
            throw new InvalidRequestException("Invalid file name");
        }
        return UUID.randomUUID() + FILE_NAME_SEPARATOR + sanitizedFileName;
    }

    private Path buildTargetPath(String folder, String fileName) {
        Path folderDirectory = folderDirectory(folder);
        try {
            Files.createDirectories(folderDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create upload directory", exception);
        }

        Path target = folderDirectory.resolve(fileName).normalize();
        if (!target.startsWith(folderDirectory)) {
            throw new InvalidRequestException("Invalid file path");
        }
        return target;
    }

    private void writeFile(MultipartFile file, Path target) {
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

    private String buildFileUrl(String folder, String fileName) {
        return uploadProperties.urlPrefix()
                + URL_SEPARATOR
                + folder
                + URL_SEPARATOR
                + fileName;
    }

    private Path folderDirectory(String folder) {
        Path baseDirectory = Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize();
        Path folderDirectory = baseDirectory.resolve(folder).normalize();
        if (!folderDirectory.startsWith(baseDirectory)) {
            throw new InvalidRequestException("Invalid file path");
        }
        return folderDirectory;
    }

    private java.util.Optional<Path> managedAvatarPath(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()
                || avatarUrl.startsWith("http://")
                || avatarUrl.startsWith("https://")) {
            return java.util.Optional.empty();
        }
        String prefix = uploadProperties.urlPrefix() + URL_SEPARATOR + AVATAR_FOLDER + URL_SEPARATOR;
        if (!avatarUrl.startsWith(prefix)) {
            return java.util.Optional.empty();
        }
        String fileName = avatarUrl.substring(prefix.length());
        if (fileName.isBlank()
                || fileName.contains(PATH_TRAVERSAL_TOKEN)
                || fileName.contains(URL_SEPARATOR)
                || fileName.contains("\\")) {
            return java.util.Optional.empty();
        }
        Path directory = folderDirectory(AVATAR_FOLDER);
        Path path = directory.resolve(fileName).normalize();
        if (!path.startsWith(directory)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(path);
    }

    private boolean deleteIfExists(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isOldRegularFile(Path path, Instant cutoff) {
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException ignored) {
            return false;
        }
    }
}
