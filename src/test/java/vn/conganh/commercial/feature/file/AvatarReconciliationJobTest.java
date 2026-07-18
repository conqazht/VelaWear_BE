package vn.conganh.commercial.feature.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.conganh.commercial.AbstractIntegrationTest;

@TestPropertySource(properties = {
        "app.upload.base-dir=target/test-uploads/avatar-reconciliation",
        "app.upload.url-prefix=/uploads"
})
@DisplayName("Avatar reconciliation job")
class AvatarReconciliationJobTest extends AbstractIntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("target/test-uploads/avatar-reconciliation");

    @Autowired
    private AvatarReconciliationJob job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() throws Exception {
        jdbcTemplate.update("delete from users where email like 'avatar-job-%@test.local'");
        deleteDirectory(UPLOAD_DIR);
    }

    @Test
    @DisplayName("cleanupOrphanedAvatars - chỉ xóa managed avatar orphan đã quá grace")
    void cleanupOrphanedAvatars_deletesOnlyOldManagedOrphans() throws Exception {
        Instant now = Instant.now();
        Path referenced = writeAvatar("referenced.jpg", now.minusSeconds(48 * 60 * 60));
        Path orphan = writeAvatar("orphan.jpg", now.minusSeconds(48 * 60 * 60));
        Path recent = writeAvatar("recent.jpg", now.minusSeconds(60));
        Path product = writeOther("products", "product.jpg", now.minusSeconds(48 * 60 * 60));
        createUser("/uploads/avatars/referenced.jpg");

        int deleted = job.cleanupOrphanedAvatars(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(referenced).exists();
        assertThat(orphan).doesNotExist();
        assertThat(recent).exists();
        assertThat(product).exists();
    }

    @Test
    @DisplayName("cleanupOrphanedAvatars - recheck DB reference ngay trước khi delete")
    void cleanupOrphanedAvatars_rechecksDatabaseReference() throws Exception {
        Instant now = Instant.now();
        Path reReferenced = writeAvatar("re-referenced.jpg", now.minusSeconds(48 * 60 * 60));
        createUser("/uploads/avatars/re-referenced.jpg");

        int deleted = job.cleanupOrphanedAvatars(now);

        assertThat(deleted).isZero();
        assertThat(reReferenced).exists();
    }

    private void createUser(String avatar) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password, birth_date, avatar, gender, security_version)
                values (?, ?, ?, ?, ?, ?, 0)
                """,
                "Avatar Job User",
                "avatar-job-" + java.util.UUID.randomUUID() + "@test.local",
                "$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq",
                java.sql.Date.valueOf(LocalDate.of(1995, 1, 1)),
                avatar,
                "OTHER");
    }

    private Path writeAvatar(String fileName, Instant lastModifiedAt) throws Exception {
        return writeOther("avatars", fileName, lastModifiedAt);
    }

    private Path writeOther(String folder, String fileName, Instant lastModifiedAt) throws Exception {
        Path directory = Files.createDirectories(UPLOAD_DIR.resolve(folder));
        Path path = Files.write(directory.resolve(fileName), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        Files.setLastModifiedTime(path, FileTime.from(lastModifiedAt));
        return path;
    }

    private void deleteDirectory(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
