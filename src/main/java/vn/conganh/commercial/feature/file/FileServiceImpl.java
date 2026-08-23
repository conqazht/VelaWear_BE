package vn.conganh.commercial.feature.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    private static final String URL_SEPARATOR = "/";
    private static final String PATH_TRAVERSAL_TOKEN = "..";
    static final String AVATAR_FOLDER = "avatars";
    private static final String RESERVED_REVIEW_FOLDER = "reviews";

    private final UploadProperties uploadProperties;
    private final ImageFileWriter imageFileWriter;

    @Override
    public FileUploadResponse store(MultipartFile file, String folder) {
        String normalizedFolder = normalizeFolder(folder);
        Path targetDirectory = folderDirectory(normalizedFolder);
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create upload directory", exception);
        }

        String storedFileName = imageFileWriter.writeWithSanitizedName(file, targetDirectory);
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
