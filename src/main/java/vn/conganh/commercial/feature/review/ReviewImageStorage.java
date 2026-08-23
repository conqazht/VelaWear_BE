package vn.conganh.commercial.feature.review;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.config.UploadProperties;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.file.ImageFileWriter;

@Service
@RequiredArgsConstructor
public class ReviewImageStorage {

    static final int MAX_IMAGE_COUNT = 5;

    private static final String REVIEW_FOLDER = "reviews";
    private static final String URL_SEPARATOR = "/";

    private final UploadProperties uploadProperties;
    private final ImageFileWriter imageFileWriter;

    public List<StoredReviewImage> store(List<MultipartFile> images) {
        List<MultipartFile> normalizedImages = images == null ? List.of() : images;
        if (normalizedImages.size() > MAX_IMAGE_COUNT) {
            throw new InvalidRequestException("A review can contain at most 5 images");
        }
        if (normalizedImages.isEmpty()) {
            return List.of();
        }

        Path reviewDirectory = reviewDirectory();
        createDirectory(reviewDirectory);
        List<StoredReviewImage> storedImages = new ArrayList<>();
        try {
            for (MultipartFile image : normalizedImages) {
                storedImages.add(storeOne(image, reviewDirectory));
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
        try (var files = Files.list(directory)) {
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

    private StoredReviewImage storeOne(MultipartFile image, Path directory) {
        // imageFileWriter validates size, extension, content-type and magic bytes,
        // then performs an atomic .tmp → target write.
        String fileName = imageFileWriter.writeWithUuidName(image, directory);
        Path targetPath = directory.resolve(fileName).normalize();
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
        if (!directory.startsWith(baseDirectory)) {
            throw new InvalidRequestException("Invalid review image path");
        }
        return directory;
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create review image directory", exception);
        }
    }

    private String buildUrl(String fileName) {
        return uploadProperties.urlPrefix()
                + URL_SEPARATOR
                + REVIEW_FOLDER
                + URL_SEPARATOR
                + fileName;
    }
}
