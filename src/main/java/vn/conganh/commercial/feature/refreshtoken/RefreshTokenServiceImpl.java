package vn.conganh.commercial.feature.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.exception.UnauthorizedException;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.refreshtoken.dto.RefreshTokenResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String REFRESH_TOKEN_HASH_ALGORITHM = "SHA-512";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RefreshTokenResponse> getAllRefreshTokens() {
        return refreshTokenRepository.findAll().stream()
                .map(RefreshTokenResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenResponse getRefreshTokenById(Long id) {
        return RefreshTokenResponse.fromEntity(findRefreshToken(id));
    }

    @Override
    @Transactional
    public RefreshTokenResponse createRefreshToken(CreateRefreshTokenRequest request) {
        User user = findUser(request.userId());

        String hashedToken = hashToken(request.token());
        if (refreshTokenRepository.existsByToken(hashedToken)) {
            throw new InvalidRequestException("Refresh token already exists");
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(hashedToken);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(request.expiresAt());
        refreshToken.setDeviceInfo(request.deviceInfo());
        refreshToken.setIpAddress(request.ipAddress());

        return RefreshTokenResponse.fromEntity(refreshTokenRepository.save(refreshToken));
    }

    @Override
    @Transactional
    public RefreshTokenResponse revokeRefreshToken(Long id) {
        RefreshToken refreshToken = findRefreshToken(id);
        if (refreshToken.isRevoked()) {
            throw new InvalidRequestException("Refresh token is already revoked");
        }
        refreshToken.setRevoked(true);
        return RefreshTokenResponse.fromEntity(refreshTokenRepository.save(refreshToken));
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken findValidRefreshToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashToken(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token is revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired");
        }

        return refreshToken;
    }

    @Override
    @Transactional
    public RefreshTokenResponse revokeRefreshToken(String rawToken) {
        RefreshToken refreshToken = findValidRefreshToken(rawToken);
        refreshToken.setRevoked(true);
        return RefreshTokenResponse.fromEntity(refreshTokenRepository.save(refreshToken));
    }

    @Override
    @Transactional
    public void deleteRefreshToken(Long id) {
        refreshTokenRepository.delete(findRefreshToken(id));
    }

    private RefreshToken findRefreshToken(Long id) {
        return refreshTokenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RefreshToken", "id", id));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(REFRESH_TOKEN_HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Refresh token hash algorithm not available", e);
        }
    }
}
