package vn.conganh.commercial.feature.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeletedAccountCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DeletedAccountCleanupJob.class);

    private final UserRepository userRepository;

    @Value("${app.user.retention-days:45}")
    private int retentionDays;

    public DeletedAccountCleanupJob(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "${app.user.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void anonymizeExpiredDeletedAccounts() {
        if (retentionDays <= 0) {
            log.warn("[VelaWear/User] - Retention days is <= 0. Skipping anonymization job.");
            return;
        }

        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("[VelaWear/User] - Starting anonymization of soft-deleted accounts deleted before: {}", cutoffTime);

        int batchSize = 100;
        int totalProcessed = 0;

        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize);
            // Must exclude already anonymized users to prevent infinite loop
            Page<User> expiredUsers = userRepository.findExpiredDeletedUsersToAnonymize(cutoffTime, pageable);

            if (expiredUsers.isEmpty()) {
                break;
            }

            for (User user : expiredUsers.getContent()) {
                anonymizeUser(user);
                totalProcessed++;
            }
        }

        log.info("[VelaWear/User] - Completed anonymization. Total accounts processed: {}", totalProcessed);
    }

    private void anonymizeUser(User user) {
        String obfuscatedId = UUID.randomUUID().toString().substring(0, 8);
        String obfuscatedEmail = "deleted_" + obfuscatedId + "@anonymized.local";
        
        user.setEmail(obfuscatedEmail);
        user.setPassword(null);
        user.setBirthDate(null);
        user.setGender(null);
        user.setFullName("Deleted User");
        
        userRepository.save(user);
        log.debug("[VelaWear/User] - Anonymized user ID: {}", user.getId());
    }
}
