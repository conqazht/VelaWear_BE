package vn.conganh.commercial.feature.refreshtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.refreshtoken.dto.RefreshTokenResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module RefreshToken - RefreshTokenServiceImpl")
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, userRepository);
    }

    @Nested
    @DisplayName("Create refresh token")
    class CreateRefreshToken {

        @Test
        @DisplayName("createRefreshToken - tạo refresh token thành công và hash token trước khi lưu")
        void createRefreshToken_validRequest_savesHashedToken() {
            // Arrange
            User user = user(1L);
            CreateRefreshTokenRequest request = createRequest("raw-refresh-token");
            String hashedToken = sha256("raw-refresh-token");
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(refreshTokenRepository.existsByToken(hashedToken)).thenReturn(false);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
                RefreshToken refreshToken = invocation.getArgument(0);
                ReflectionTestUtils.setField(refreshToken, "id", 10L);
                return refreshToken;
            });

            // Act
            RefreshTokenResponse response = refreshTokenService.createRefreshToken(request);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.revoked()).isFalse();
            verify(refreshTokenRepository).save(argThat(refreshToken ->
                    hashedToken.equals(refreshToken.getToken())
                            && !"raw-refresh-token".equals(refreshToken.getToken())));
        }

        @Test
        @DisplayName("createRefreshToken - không gọi save khi token đã tồn tại")
        void createRefreshToken_duplicateToken_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateRefreshTokenRequest request = createRequest("raw-refresh-token");
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user(1L)));
            when(refreshTokenRepository.existsByToken(sha256("raw-refresh-token"))).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.createRefreshToken(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already exists");
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("createRefreshToken - không gọi save khi user không tồn tại")
        void createRefreshToken_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateRefreshTokenRequest request = createRequest("raw-refresh-token");
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.createRefreshToken(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read refresh token")
    class ReadRefreshToken {

        @Test
        @DisplayName("getAllRefreshTokens - trả về danh sách refresh token")
        void getAllRefreshTokens_existingTokens_returnsResponses() {
            // Arrange
            when(refreshTokenRepository.findAll()).thenReturn(List.of(refreshToken(10L, user(1L), false)));

            // Act
            List<RefreshTokenResponse> responses = refreshTokenService.getAllRefreshTokens();

            // Assert
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getRefreshTokenById - ném ResourceNotFoundException khi không tìm thấy token")
        void getRefreshTokenById_missingToken_throwsResourceNotFoundException() {
            // Arrange
            when(refreshTokenRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.getRefreshTokenById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Revoke refresh token")
    class RevokeRefreshToken {

        @Test
        @DisplayName("revokeRefreshToken - revoke token thành công khi token chưa bị revoke")
        void revokeRefreshToken_activeToken_returnsRevokedToken() {
            // Arrange
            RefreshToken refreshToken = refreshToken(10L, user(1L), false);
            when(refreshTokenRepository.findById(10L)).thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(refreshToken)).thenReturn(refreshToken);

            // Act
            RefreshTokenResponse response = refreshTokenService.revokeRefreshToken(10L);

            // Assert
            assertThat(response.revoked()).isTrue();
        }

        @Test
        @DisplayName("revokeRefreshToken - không gọi save khi token đã bị revoke")
        void revokeRefreshToken_alreadyRevoked_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            RefreshToken refreshToken = refreshToken(10L, user(1L), true);
            when(refreshTokenRepository.findById(10L)).thenReturn(Optional.of(refreshToken));

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.revokeRefreshToken(10L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already revoked");
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("revokeAllByUserId - revoke toàn bộ refresh-token audit row bằng bulk update")
        void revokeAllByUserId_allAuditRows_returnsAffectedRows() {
            // Arrange
            when(refreshTokenRepository.revokeAllByUserId(1L))
                    .thenReturn(3);

            // Act
            int revoked = refreshTokenService.revokeAllByUserId(1L);

            // Assert
            assertThat(revoked).isEqualTo(3);
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }

    @Nested
    @DisplayName("Delete refresh token")
    class DeleteRefreshToken {

        @Test
        @DisplayName("deleteRefreshToken - xóa refresh token khi id tồn tại")
        void deleteRefreshToken_existingToken_deletesToken() {
            // Arrange
            RefreshToken refreshToken = refreshToken(10L, user(1L), false);
            when(refreshTokenRepository.findById(10L)).thenReturn(Optional.of(refreshToken));

            // Act
            refreshTokenService.deleteRefreshToken(10L);

            // Assert
            verify(refreshTokenRepository).delete(refreshToken);
        }
    }

    private CreateRefreshTokenRequest createRequest(String token) {
        return new CreateRefreshTokenRequest(
                1L,
                token,
                Instant.parse("2026-12-31T00:00:00Z"),
                "Chrome",
                "127.0.0.1");
    }

    private RefreshToken refreshToken(Long id, User user, boolean revoked) {
        RefreshToken refreshToken = new RefreshToken();
        ReflectionTestUtils.setField(refreshToken, "id", id);
        refreshToken.setToken(sha256("raw-refresh-token"));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.parse("2026-12-31T00:00:00Z"));
        refreshToken.setRevoked(revoked);
        refreshToken.setDeviceInfo("Chrome");
        refreshToken.setIpAddress("127.0.0.1");
        return refreshToken;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Refresh token hash algorithm not available", e);
        }
    }
}
