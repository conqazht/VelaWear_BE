package vn.conganh.commercial.feature.file;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.security.monitoring.SecurityEventLogger;
import vn.conganh.commercial.security.monitoring.SecurityMetrics;

@Component
@RequiredArgsConstructor
public class AvatarReconciliationJob {

    private final FileServiceImpl fileService;
    private final UserRepository userRepository;
    private final SecurityMetrics securityMetrics;
    private final SecurityEventLogger eventLogger;

    @Value("${app.upload.avatar.orphan-grace:24h}")
    private Duration orphanGrace;

    @Scheduled(cron = "${app.upload.avatar.reconciliation-cron:0 0 */6 * * *}")
    public void cleanupOrphanedAvatars() {
        cleanupOrphanedAvatars(Instant.now());
    }

    @Transactional(readOnly = true)
    public int cleanupOrphanedAvatars(Instant now) {
        Set<String> referenced = new HashSet<>(userRepository.findManagedAvatarReferences());
        int deleted = 0;
        for (String candidate : fileService.managedAvatarUrlsOlderThan(now.minus(orphanGrace))) {
            if (referenced.contains(candidate) || userRepository.existsByAvatar(candidate)) {
                securityMetrics.avatarCleanup("skipped", "referenced");
                continue;
            }
            if (fileService.deleteManagedAvatar(candidate)) {
                deleted++;
                securityMetrics.avatarCleanup("deleted", "orphan");
                eventLogger.event("avatar_cleanup", "success", "orphan_deleted", null, null, null);
            } else {
                securityMetrics.avatarCleanup("failure", "delete_failed");
                eventLogger.warning("avatar_cleanup", "delete_failed", null);
            }
        }
        return deleted;
    }
}
