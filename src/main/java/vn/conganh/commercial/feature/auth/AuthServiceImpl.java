package vn.conganh.commercial.feature.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.security.TokenBlacklistService;
import vn.conganh.commercial.security.ClientIpResolver;
import vn.conganh.commercial.security.monitoring.SecurityEventLogger;
import vn.conganh.commercial.security.monitoring.SecurityMetrics;
import vn.conganh.commercial.security.ratelimit.AuthRateLimitService;
import vn.conganh.commercial.security.session.SessionRevocationReason;
import vn.conganh.commercial.security.session.SessionRevocationService;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.RefreshTokenSessionNotFoundException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.exception.SessionRevokedException;
import vn.conganh.commercial.exception.UnauthorizedException;
import vn.conganh.commercial.feature.auth.dto.ChangeEmailRequest;
import vn.conganh.commercial.feature.auth.dto.ChangePasswordRequest;
import vn.conganh.commercial.feature.auth.dto.ForgotPasswordResetRequest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.OAuth2ExchangeRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.oauth2.OAuth2LoginCodeService;
import vn.conganh.commercial.feature.auth.otp.OtpService;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSession;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserHasRole;
import vn.conganh.commercial.feature.user.UserHasRoleRepository;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.user.dto.UserResponse;
import vn.conganh.commercial.util.constant.OtpPurpose;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHasRoleRepository userHasRoleRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenCodec authTokenCodec;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final OtpService otpService;
    private final OAuth2LoginCodeService oauth2LoginCodeService;
    private final AuthRateLimitService rateLimitService;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityMetrics securityMetrics;
    private final SecurityEventLogger securityEventLogger;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserHasRoleRepository userHasRoleRepository,
            RefreshTokenService refreshTokenService,
            RefreshTokenSessionService refreshTokenSessionService,
            PasswordEncoder passwordEncoder,
            AuthTokenCodec authTokenCodec,
            JwtProperties jwtProperties,
            TokenBlacklistService tokenBlacklistService,
            OtpService otpService,
            OAuth2LoginCodeService oauth2LoginCodeService,
            AuthRateLimitService rateLimitService,
            SessionRevocationService sessionRevocationService,
            SecurityMetrics securityMetrics,
            SecurityEventLogger securityEventLogger) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userHasRoleRepository = userHasRoleRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.passwordEncoder = passwordEncoder;
        this.authTokenCodec = authTokenCodec;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.otpService = otpService;
        this.oauth2LoginCodeService = oauth2LoginCodeService;
        this.rateLimitService = rateLimitService;
        this.sessionRevocationService = sessionRevocationService;
        this.securityMetrics = securityMetrics;
        this.securityEventLogger = securityEventLogger;
    }

    @Override
    @Transactional
    public TokenResponse authenticate(LoginRequest request) {
        return authenticate(request, null, null);
    }

    @Override
    @Transactional
    public TokenResponse authenticate(LoginRequest request, String deviceInfo, String ipAddress) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
        String ipRateLimitPrefix = ClientIpResolver.fromAddress(normalizedIp).rateLimitPrefix();
        Map<String, String> identityLimits = Map.of(
                "account", normalizedEmail,
                "ip-account", ipRateLimitPrefix + ':' + normalizedEmail);
        rateLimitService.enforce("login", "AUTH_RATE_LIMITED", identityLimits, ipRateLimitPrefix);

        try {
            userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                    .filter(user -> user.getPassword() == null || user.getPassword().isBlank())
                    .ifPresent(user -> {
                        throw new UnauthorizedException("This account uses Google login. Please continue with Google.");
                    });

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

            String email = authentication.getName();
            User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            TokenResponse response = issueTokens(user, roles, deviceInfo, ipAddress);

            rateLimitService.clear("login", identityLimits);
            securityMetrics.authAttempt("login", "success");
            securityEventLogger.event(
                    "auth_login", "success", "authenticated", user.getId(), null,
                    rateLimitService.subjectHash("client-ip", normalizedIp));
            return response;
        } catch (RuntimeException exception) {
            securityMetrics.authAttempt("login", "failure");
            securityEventLogger.event(
                    "auth_login", "failure", "invalid_credentials", null, null,
                    rateLimitService.subjectHash("client-ip", normalizedIp));
            throw exception;
        }
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "USER"));
        otpService.consumeProof(request.otpProofToken(), normalizedEmail, OtpPurpose.REGISTER);

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBirthDate(request.birthDate());
        user.setAvatar(request.avatar());
        user.setGender(request.gender());
        User savedUser = userRepository.save(user);

        UserHasRole userHasRole = new UserHasRole();
        userHasRole.setUser(savedUser);
        userHasRole.setRole(userRole);
        userHasRoleRepository.save(userHasRole);

        log.info("[VelaWear/Auth] - REGISTER: userId: {}", savedUser.getId());
        securityMetrics.authAttempt("register", "success");
        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public TokenResponse exchangeOAuth2Code(OAuth2ExchangeRequest request, String deviceInfo, String ipAddress) {
        Long userId = oauth2LoginCodeService.consume(request.code());
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UnauthorizedException("OAuth2 login user is invalid"));
        List<String> roles = userRepository.findRolesByUserId(user.getId()).stream()
                .map(role -> "ROLE_" + role.getName())
                .toList();
        TokenResponse response = issueTokens(user, roles, deviceInfo, ipAddress);
        log.info("[VelaWear/Auth] - OAUTH2_EXCHANGE: userId: {}", user.getId());
        securityMetrics.authAttempt("oauth_exchange", "success");
        return response;
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        Jwt currentRefreshJwt = validateRefreshJwt(request.refreshToken());
        String currentJti = requireJti(currentRefreshJwt);
        long tokenSecurityVersion = requireSecurityVersion(currentRefreshJwt);
        rateLimitService.enforce(
                "refresh",
                "AUTH_RATE_LIMITED",
                Map.of("session", currentJti));

        RefreshTokenSession currentSession = refreshTokenSessionService.find(currentJti)
                .orElseThrow(() -> {
                    refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
                    return new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
                });

        String currentTokenHash = refreshTokenService.hashToken(request.refreshToken());
        if (!currentTokenHash.equals(currentSession.tokenHash())
                || currentSession.securityVersion() != tokenSecurityVersion) {
            refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
            throw new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(currentSession.userId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token user is invalid"));
        if (user.getSecurityVersion() != tokenSecurityVersion) {
            refreshTokenSessionService.delete(currentJti);
            refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
            securityMetrics.authAttempt("refresh", "session_revoked");
            throw new SessionRevokedException();
        }

        List<String> roles = userRepository.findRolesByUserId(user.getId()).stream()
                .map(role -> "ROLE_" + role.getName())
                .toList();
        String refreshToken = rotateRefreshToken(request.refreshToken(), user, currentSession);
        String accessToken = generateAccessToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), roles);

        log.info("[VelaWear/Auth] - REFRESH_TOKEN: userId: {}", user.getId());
        securityMetrics.authAttempt("refresh", "success");

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Object userIdClaim = jwt.getClaim("userId");
            if (userIdClaim instanceof Number number) {
                rateLimitService.enforce(
                        "auth-session",
                        "AUTH_RATE_LIMITED",
                        Map.of("user", String.valueOf(number.longValue())));
            }
            if (jwt.getExpiresAt() != null) {
                long remainingSeconds = jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
                if (remainingSeconds > 0) {
                    // Chỉ blacklist theo thời gian còn lại của token để khóa Redis không sống lâu hơn cần thiết.
                    tokenBlacklistService.blacklistToken(jwt.getTokenValue(), remainingSeconds);
                }
            }
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            Jwt refreshJwt = validateRefreshJwt(request.refreshToken());
            String refreshJti = requireJti(refreshJwt);
            rateLimitService.enforce(
                    "auth-session",
                    "AUTH_RATE_LIMITED",
                    Map.of("session", refreshJti));
            refreshTokenSessionService.delete(refreshJti);
            refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        rateLimitService.enforce(
                "auth-session",
                "AUTH_RATE_LIMITED",
                Map.of("user", String.valueOf(user.getId())));

        List<UserResponse.RoleSummaryResponse> roles = userRepository.findRolesByUserId(user.getId()).stream()
                .map(UserResponse.RoleSummaryResponse::fromEntity)
                .toList();
        return UserResponse.fromEntity(user, roles);
    }

    private TokenResponse issueTokens(User user, List<String> roles, String deviceInfo, String ipAddress) {
        String accessToken = generateAccessToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), roles);
        String refreshToken = createRefreshToken(user, deviceInfo, ipAddress);
        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    private String generateAccessToken(
            String email,
            Long userId,
            long securityVersion,
            List<String> roles) {
        return authTokenCodec.generateAccessToken(email, userId, securityVersion, roles);
    }

    private String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();
        String refreshToken = generateRefreshToken(user, now, expiresAt, jti);
        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                refreshToken,
                expiresAt,
                deviceInfo,
                ipAddress
        ));
        refreshTokenSessionService.create(new RefreshTokenSession(
                jti,
                user.getId(),
                user.getSecurityVersion(),
                refreshTokenService.hashToken(refreshToken),
                deviceInfo,
                ipAddress,
                now,
                expiresAt));
        return refreshToken;
    }

    private String rotateRefreshToken(
            String currentRefreshToken,
            User user,
            RefreshTokenSession currentSession) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS);
        String newJti = UUID.randomUUID().toString();
        String newRefreshToken = generateRefreshToken(user, now, expiresAt, newJti);
        refreshTokenService.markRefreshTokenRevoked(currentRefreshToken);
        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                newRefreshToken,
                expiresAt,
                currentSession.deviceInfo(),
                currentSession.ipAddress()
        ));
        RefreshTokenSession replacementSession = new RefreshTokenSession(
                newJti,
                user.getId(),
                currentSession.securityVersion(),
                refreshTokenService.hashToken(newRefreshToken),
                currentSession.deviceInfo(),
                currentSession.ipAddress(),
                now,
                expiresAt);
        if (!refreshTokenSessionService.rotateIfCurrent(currentSession, replacementSession)) {
            throw new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
        }
        return newRefreshToken;
    }

    private String generateRefreshToken(User user, Instant issuedAt, Instant expiresAt, String jti) {
        return authTokenCodec.generateRefreshToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), issuedAt, expiresAt, jti);
    }

    private Jwt validateRefreshJwt(String rawRefreshToken) {
        return authTokenCodec.validateAndDecodeRefreshJwt(rawRefreshToken);
    }

    private String requireJti(Jwt jwt) {
        return authTokenCodec.requireJti(jwt);
    }

    private long requireSecurityVersion(Jwt jwt) {
        return authTokenCodec.requireSecurityVersion(jwt);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void resetPassword(ForgotPasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        otpService.consumeProof(
                request.otpProofToken(), normalizedEmail, OtpPurpose.FORGOT_PASSWORD);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        sessionRevocationService.revokeAll(user, SessionRevocationReason.PASSWORD_RESET);
        userRepository.save(user);

        log.info("[VelaWear/Auth] - PASSWORD_RESET: userId: {}", user.getId());
        securityMetrics.authAttempt("password_reset", "success");
    }

    @Override
    @Transactional
    public void changeEmail(String currentEmail, ChangeEmailRequest request) {
        String normalizedCurrent = normalizeEmail(currentEmail);
        String normalizedNew = normalizeEmail(request.newEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedCurrent)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedCurrent));
        rateLimitService.enforce(
                "sensitive-change",
                "AUTH_RATE_LIMITED",
                Map.of("user", String.valueOf(user.getId())));

        if (userRepository.existsByEmail(normalizedNew)) {
            throw new DuplicateResourceException("User", "email", normalizedNew);
        }

        otpService.consumeProof(
                request.otpProofToken(), normalizedNew, OtpPurpose.CHANGE_EMAIL, user.getId());
        user.setEmail(normalizedNew);
        sessionRevocationService.revokeAll(user, SessionRevocationReason.EMAIL_CHANGE);
        userRepository.save(user);

        log.info("[VelaWear/Auth] - CHANGE_EMAIL: userId: {}", user.getId());
        securityMetrics.authAttempt("email_change", "success");
    }

    @Override
    @Transactional
    public void changePassword(String currentEmail, ChangePasswordRequest request) {
        String normalizedEmail = normalizeEmail(currentEmail);

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));
        rateLimitService.enforce(
                "sensitive-change",
                "AUTH_RATE_LIMITED",
                Map.of("user", String.valueOf(user.getId())));

        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (hasPassword) {
            String currentPassword = request.currentPassword();
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new InvalidRequestException("Current password is required.");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new UnauthorizedException("Current password is incorrect.");
            }
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        sessionRevocationService.revokeAll(user, SessionRevocationReason.PASSWORD_CHANGE);
        userRepository.save(user);

        log.info("[VelaWear/Auth] - CHANGE_PASSWORD: userId: {}, hadPassword: {}", user.getId(), hasPassword);
        securityMetrics.authAttempt("password_change", "success");
    }

    @Override
    @Transactional
    public void deleteMe(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        user.setDeletedAt(java.time.Instant.now());
        sessionRevocationService.revokeAll(user, SessionRevocationReason.ACCOUNT_DELETED);
        userRepository.save(user);

        log.info("[VelaWear/Auth] - DELETE_ME: userId: {}", user.getId());
    }
}
