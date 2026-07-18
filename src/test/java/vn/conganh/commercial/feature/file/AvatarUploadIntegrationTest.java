package vn.conganh.commercial.feature.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import vn.conganh.commercial.AuthenticatedIntegrationTest;

@TestPropertySource(properties = {
        "app.upload.base-dir=target/test-uploads/avatar-upload",
        "app.upload.url-prefix=/uploads",
        "app.security.rate-limit.enabled=true"
})
@DisplayName("Avatar upload lifecycle")
class AvatarUploadIntegrationTest extends AuthenticatedIntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("target/test-uploads/avatar-upload");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() throws Exception {
        jdbcTemplate.update("delete from user_role where user_id in (select id from users where email like 'avatar-%@test.local')");
        jdbcTemplate.update("delete from users where email like 'avatar-%@test.local'");
        deleteDirectory(UPLOAD_DIR);
    }

    @Test
    @DisplayName("PUT /api/v1/files/avatar - thay avatar self-scoped và xóa avatar cũ sau commit")
    void uploadAvatar_validReplacement_updatesUserAndDeletesPreviousLocalAvatar() throws Exception {
        TestUser user = createUser("/uploads/avatars/old.jpg");
        Path oldAvatar = writeUpload("avatars", "old.jpg");

        mockMvc.perform(putAvatar(user.token(), image("avatar.jpg", "image/jpeg", jpegBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value(startsWith("/uploads/avatars/")))
                .andExpect(jsonPath("$.data.email").value(user.email()));

        String currentAvatar = avatar(user.id());
        assertThat(currentAvatar).startsWith("/uploads/avatars/");
        assertThat(currentAvatar).isNotEqualTo("/uploads/avatars/old.jpg");
        assertThat(UPLOAD_DIR.resolve("avatars").resolve(currentAvatar.substring("/uploads/avatars/".length())))
                .exists();
        assertThat(oldAvatar).doesNotExist();
    }

    @Test
    @DisplayName("PUT /api/v1/files/avatar - không xóa external/product/review avatar cũ")
    void uploadAvatar_previousNonManagedAvatar_preservesPreviousReferenceTarget() throws Exception {
        TestUser external = createUser("https://cdn.test.local/avatar.jpg");
        mockMvc.perform(putAvatar(external.token(), image("external.jpg", "image/jpeg", jpegBytes())))
                .andExpect(status().isOk());

        Path product = writeUpload("products", "product.jpg");
        TestUser productUser = createUser("/uploads/products/product.jpg");
        mockMvc.perform(putAvatar(productUser.token(), image("product-user.jpg", "image/jpeg", jpegBytes())))
                .andExpect(status().isOk());

        Path review = writeUpload("reviews", "review.jpg");
        TestUser reviewUser = createUser("/uploads/reviews/review.jpg");
        mockMvc.perform(putAvatar(reviewUser.token(), image("review-user.jpg", "image/jpeg", jpegBytes())))
                .andExpect(status().isOk());

        assertThat(product).exists();
        assertThat(review).exists();
    }

    @Test
    @DisplayName("PUT /api/v1/files/avatar - rate limit trả AUTH_RATE_LIMITED trước khi tạo file mới")
    void uploadAvatar_userRateLimited_doesNotCreateAdditionalFileOrChangeAvatar() throws Exception {
        TestUser user = createUser(null);

        for (int index = 1; index <= 5; index++) {
            mockMvc.perform(putAvatar(user.token(), image("avatar-" + index + ".jpg", "image/jpeg", jpegBytes())))
                    .andExpect(status().isOk());
        }
        String firstAvatar = avatar(user.id());
        long fileCountAfterFirstUpload = avatarFileCount();

        mockMvc.perform(putAvatar(user.token(), image("second.jpg", "image/jpeg", jpegBytes())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());

        assertThat(avatar(user.id())).isEqualTo(firstAvatar);
        assertThat(avatarFileCount()).isEqualTo(fileCountAfterFirstUpload);
    }

    @Test
    @DisplayName("PUT /api/v1/users/me - field avatar gửi thừa không đổi avatar")
    void updateMyProfile_extraAvatarField_doesNotBypassAvatarEndpoint() throws Exception {
        TestUser user = createUser("/uploads/avatars/original.jpg");

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Avatar User",
                                  "birthDate": "1995-01-01",
                                  "gender": "OTHER",
                                  "avatar": "https://attacker.invalid/replaced.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value("/uploads/avatars/original.jpg"));

        assertThat(avatar(user.id())).isEqualTo("/uploads/avatars/original.jpg");
    }

    private MockMultipartHttpServletRequestBuilder putAvatar(String token, MockMultipartFile file) {
        return multipart("/api/v1/files/avatar")
                .file(file)
                .header("Authorization", "Bearer " + token)
                .with(request -> {
                    request.setMethod("PUT");
                    request.setRemoteAddr("203.0.113." + Math.abs(token.hashCode() % 200 + 1));
                    return request;
                });
    }

    private TestUser createUser(String avatar) {
        String email = "avatar-" + UUID.randomUUID() + "@test.local";
        Long userId = jdbcTemplate.queryForObject("""
                insert into users (full_name, email, password, birth_date, avatar, gender, security_version)
                values (?, ?, ?, ?, ?, ?, 0)
                returning id
                """, Long.class,
                "Avatar User",
                email,
                "$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq",
                java.sql.Date.valueOf(LocalDate.of(1995, 1, 1)),
                avatar,
                "OTHER");
        jdbcTemplate.update("""
                insert into user_role (user_id, role_id)
                select ?, id from roles where name = 'USER'
                """, userId);
        return new TestUser(userId, email, tokenWithRoles(email, userId, List.of("ROLE_USER")));
    }

    private String avatar(Long userId) {
        return jdbcTemplate.queryForObject("select avatar from users where id = ?", String.class, userId);
    }

    private long avatarFileCount() throws Exception {
        Path avatarDirectory = UPLOAD_DIR.resolve("avatars");
        if (!Files.isDirectory(avatarDirectory)) {
            return 0;
        }
        try (var files = Files.list(avatarDirectory)) {
            return files.count();
        }
    }

    private Path writeUpload(String folder, String fileName) throws Exception {
        Path directory = Files.createDirectories(UPLOAD_DIR.resolve(folder));
        return Files.write(directory.resolve(fileName), jpegBytes());
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

    private MockMultipartFile image(String originalFileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", originalFileName, contentType, content);
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private record TestUser(Long id, String email, String token) {
    }
}
