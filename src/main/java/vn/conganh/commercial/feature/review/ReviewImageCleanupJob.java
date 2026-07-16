package vn.conganh.commercial.feature.review;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewImageCleanupJob {

    private static final Duration ORPHAN_MINIMUM_AGE = Duration.ofHours(24);

    private final ReviewImageRepository reviewImageRepository;
    private final ReviewImageStorage reviewImageStorage;

    @Scheduled(cron = "${app.review.image-cleanup-cron:0 30 2 * * *}")
    public void cleanupOrphanedImages() {
        try {
            Set<String> referencedImages = new HashSet<>(reviewImageRepository.findAllImagePaths());
            Instant cutoff = Instant.now().minus(ORPHAN_MINIMUM_AGE);
            int deletedCount = reviewImageStorage.deleteUnreferencedOlderThan(cutoff, referencedImages);
            log.info("[VelaWear/Review] - CLEANUP_IMAGES: deletedCount: {}", deletedCount);
        } catch (Exception exception) {
            log.error("[VelaWear/Review] - CLEANUP_IMAGES: failed", exception);
        }
    }
}
