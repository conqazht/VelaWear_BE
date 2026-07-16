package vn.conganh.commercial.feature.review;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;

@Service
@RequiredArgsConstructor
public class ReviewImageStorage {

    static final int MAX_IMAGE_COUNT = 5;

    private static final int MAX_SIGNATURE_BYTES = 12;
    private static final int WEBP_FORMAT_OFFSET = 8;
    private static final String REVIEW_FOLDER = "reviews";
    private static final String URL_SEPARATOR = "/";
    private static final Set<String> REVIEW_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final UploadProperties uploadProperties;

    public List<StoredReviewImage> store(List<MultipartFile> images) {
        List<MultipartFile> normalizedImages = images == null ? List.of() : images;
        if (normalizedImages.size() > MAX_IMAGE_COUNT) {
            throw new InvalidRequestException("A review can contain at most 5 images");
        }

        List<String> extensions = normalizedImages.stream()
                .map(this::validateAndGetExtension)
                .toList();
        if (normalizedImages.isEmpty()) {
            return List.of();
        }

        Path reviewDirectory = reviewDirectory();
        createDirectory(reviewDirectory);
        List<StoredReviewImage> storedImages = new ArrayList<>();
        try {
            for (int index = 0; index < normalizedImages.size(); index++) {
                storedImages.add(storeOne(normalizedImages.get(index), extensions.get(index), reviewDirectory));
            }
            return List.copyOf(storedImages);
        } catch (RuntimeException exception) {
            delete(storedImages);
            throw exception;
        }
    }

    public void delete(List<StoredReviewImage> storedImages) {
        if (storedImages == null) {
            return;
        }
        for (StoredReviewImage storedImage : storedImages) {
            try {
                Files.deleteIfExists(storedImage.path());
            } catch (IOException ignored) {
                // A later orphan-cleanup run retries files that could not be removed during rollback.
            }
        }
    }

    public int deleteUnreferencedOlderThan(Instant cutoff, Set<String> referencedUrls) {
        Path directory = reviewDirectory();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }

        List<Path> candidates;
        try (Stream<Path> files = Files.list(directory)) {
            candidates = files.toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan review image directory", exception);
        }

        int deletedCount = 0;
        for (Path candidate : candidates) {
            if (deleteIfOrphan(candidate, cutoff, referencedUrls)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private String validateAndGetExtension(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidRequestException("Review image must not be empty");
        }
        if (image.getSize() > uploadProperties.maxSizeBytes()) {
            throw new InvalidRequestException("Review image size exceeds 5 MB");
        }

        String extension = getExtension(image.getOriginalFilename());
        if (!REVIEW_EXTENSIONS.contains(extension)
                || !uploadProperties.allowedExtensions().contains(extension)) {
            throw new InvalidRequestException("Review image extension is not allowed");
        }
        validateContentType(image, extension);
        validateSignature(image, extension);
        return extension;
    }

    private String getExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()
                || originalFileName.contains("..")
                || originalFileName.contains("/")
                || originalFileName.contains("\\")) {
            throw new InvalidRequestException("Invalid review image file name");
        }

        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == originalFileName.length() - 1) {
            throw new InvalidRequestException("Review image extension is required");
        }
        return originalFileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateContentType(MultipartFile image, String extension) {
        String expectedContentType = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "";
        };
        if (!expectedContentType.equals(image.getContentType())) {
            throw new InvalidRequestException("Review image content type does not match its extension");
        }
    }

    private void validateSignature(MultipartFile image, String extension) {
        byte[] signature;
        try (InputStream inputStream = image.getInputStream()) {
            signature = inputStream.readNBytes(MAX_SIGNATURE_BYTES);
        } catch (IOException exception) {
            throw new InvalidRequestException("Could not read review image content");
        }

        boolean validSignature = switch (extension) {
            case "jpg", "jpeg" -> hasBytesAt(signature, 0, 0xFF, 0xD8, 0xFF);
            case "png" -> hasBytesAt(signature, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "webp" -> hasBytesAt(signature, 0, 0x52, 0x49, 0x46, 0x46)
                    && hasBytesAt(signature, WEBP_FORMAT_OFFSET, 0x57, 0x45, 0x42, 0x50);
            default -> false;
        };
        if (!validSignature) {
            throw new InvalidRequestException("Review image content does not match its extension");
        }
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

    private StoredReviewImage storeOne(MultipartFile image, String extension, Path directory) {
        String identifier = UUID.randomUUID().toString();
        String fileName = identifier + "." + extension;
        Path temporaryPath = directory.resolve(identifier + ".tmp").normalize();
        Path targetPath = directory.resolve(fileName).normalize();
        ensureInsideDirectory(directory, temporaryPath);
        ensureInsideDirectory(directory, targetPath);

        try (InputStream inputStream = image.getInputStream()) {
            Files.copy(inputStream, temporaryPath);
            Files.move(temporaryPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            deleteTemporaryFile(temporaryPath);
            throw new IllegalStateException("Could not store review image", exception);
        }
        return new StoredReviewImage(buildUrl(fileName), targetPath);
    }

    private boolean deleteIfOrphan(Path candidate, Instant cutoff, Set<String> referencedUrls) {
        try {
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getLastModifiedTime(candidate).toInstant().isBefore(cutoff)) {
                return false;
            }
            String url = buildUrl(candidate.getFileName().toString());
            if (referencedUrls.contains(url)) {
                return false;
            }
            return Files.deleteIfExists(candidate);
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path reviewDirectory() {
        Path baseDirectory = Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize();
        Path directory = baseDirectory.resolve(REVIEW_FOLDER).normalize();
        ensureInsideDirectory(baseDirectory, directory);
        return directory;
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create review image directory", exception);
        }
    }

    private void ensureInsideDirectory(Path directory, Path candidate) {
        if (!candidate.startsWith(directory)) {
            throw new InvalidRequestException("Invalid review image path");
        }
    }

    private String buildUrl(String fileName) {
        return uploadProperties.urlPrefix()
                + URL_SEPARATOR
                + REVIEW_FOLDER
                + URL_SEPARATOR
                + fileName;
    }

    private void deleteTemporaryFile(Path temporaryPath) {
        try {
            Files.deleteIfExists(temporaryPath);
        } catch (IOException ignored) {
            // The scheduled orphan cleanup removes abandoned temporary files after 24 hours.
        }
    }
}
