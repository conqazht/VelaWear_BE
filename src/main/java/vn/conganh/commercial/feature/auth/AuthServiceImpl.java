package vn.conganh.commercial.feature.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.security.TokenBlacklistService;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.RefreshTokenSessionNotFoundException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
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
import vn.conganh.commercial.feature.refreshtoken.RefreshToken;
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

    private static final MacAlgorithm JWT_MAC_ALGORITHM = MacAlgorithm.HS512;
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHasRoleRepository userHasRoleRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder accessJwtEncoder;
    private final JwtEncoder refreshJwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final OtpService otpService;
    private final OAuth2LoginCodeService oauth2LoginCodeService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserHasRoleRepository userHasRoleRepository,
            RefreshTokenService refreshTokenService,
            RefreshTokenSessionService refreshTokenSessionService,
            PasswordEncoder passwordEncoder,
            JwtEncoder accessJwtEncoder,
            @Qualifier("refreshJwtEncoder") JwtEncoder refreshJwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            JwtProperties jwtProperties,
            TokenBlacklistService tokenBlacklistService,
            OtpService otpService,
            OAuth2LoginCodeService oauth2LoginCodeService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userHasRoleRepository = userHasRoleRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.passwordEncoder = passwordEncoder;
        this.accessJwtEncoder = accessJwtEncoder;
        this.refreshJwtEncoder = refreshJwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.otpService = otpService;
        this.oauth2LoginCodeService = oauth2LoginCodeService;
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

        log.info("[VelaWear/Auth] - LOGIN: userId: {}, email: {}", user.getId(), email);

        return response;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }

        if (!otpService.isOtpVerified(normalizedEmail, OtpPurpose.REGISTER)) {
            throw new InvalidRequestException("Email address has not been verified with OTP.");
        }
        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBirthDate(request.birthDate());
        user.setAvatar(request.avatar());
        user.setGender(request.gender());
        User savedUser = userRepository.save(user);

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "USER"));
        UserHasRole userHasRole = new UserHasRole();
        userHasRole.setUser(savedUser);
        userHasRole.setRole(userRole);
        userHasRoleRepository.save(userHasRole);

        otpService.consumeOtpVerifiedMarker(normalizedEmail, OtpPurpose.REGISTER);
        log.info("[VelaWear/Auth] - REGISTER: userId: {}", savedUser.getId());
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
        log.info("[VelaWear/Auth] - OAUTH2_EXCHANGE: userId: {}, email: {}", user.getId(), user.getEmail());
        return response;
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        Jwt currentRefreshJwt = validateRefreshJwt(request.refreshToken());
        String currentJti = requireJti(currentRefreshJwt);
        RefreshTokenSession currentSession = refreshTokenSessionService.find(currentJti)
                .orElseThrow(() -> {
                    refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
                    return new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
                });

        String currentTokenHash = refreshTokenService.hashToken(request.refreshToken());
        if (!currentTokenHash.equals(currentSession.tokenHash())) {
            refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
            throw new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(currentSession.userId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token user is invalid"));

        List<String> roles = userRepository.findRolesByUserId(user.getId()).stream()
                .map(role -> "ROLE_" + role.getName())
                .toList();
        String refreshToken = rotateRefreshToken(request.refreshToken(), user, currentSession);
        String accessToken = generateAccessToken(user.getEmail(), user.getId(), roles);

        log.info("[VelaWear/Auth] - REFRESH_TOKEN: userId: {}", user.getId());

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
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
            refreshTokenSessionService.delete(requireJti(refreshJwt));
            refreshTokenService.markRefreshTokenRevoked(request.refreshToken());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        List<UserResponse.RoleSummaryResponse> roles = userRepository.findRolesByUserId(user.getId()).stream()
                .map(UserResponse.RoleSummaryResponse::fromEntity)
                .toList();
        return UserResponse.fromEntity(user, roles);
    }

    private TokenResponse issueTokens(User user, List<String> roles, String deviceInfo, String ipAddress) {
        String accessToken = generateAccessToken(user.getEmail(), user.getId(), roles);
        String refreshToken = createRefreshToken(user, deviceInfo, ipAddress);
        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    private String generateAccessToken(String email, Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenExpiration(), ChronoUnit.SECONDS))
                .build();

        JwsHeader header = JwsHeader.with(JWT_MAC_ALGORITHM).build();
        return accessJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("type", REFRESH_TOKEN_TYPE)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(JWT_MAC_ALGORITHM).build();
        return refreshJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Jwt validateRefreshJwt(String rawRefreshToken) {
        try {
            Jwt jwt = refreshJwtDecoder.decode(rawRefreshToken);
            if (!REFRESH_TOKEN_TYPE.equals(jwt.getClaimAsString("type"))) {
                throw new UnauthorizedException("Refresh token type is invalid");
            }
            return jwt;
        } catch (JwtException exception) {
            throw new UnauthorizedException("Refresh token is invalid");
        }
    }

    private String requireJti(Jwt jwt) {
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw new UnauthorizedException("Refresh token id is invalid");
        }
        return jwt.getId();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void resetPassword(ForgotPasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (!otpService.isOtpVerified(normalizedEmail, OtpPurpose.FORGOT_PASSWORD)) {
            throw new InvalidRequestException("Email address has not been verified with OTP.");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        otpService.consumeOtpVerifiedMarker(normalizedEmail, OtpPurpose.FORGOT_PASSWORD);
        log.info("[VelaWear/Auth] - PASSWORD_RESET: email: {}", normalizedEmail);
    }

    @Override
    @Transactional
    public void changeEmail(String currentEmail, ChangeEmailRequest request) {
        String normalizedCurrent = normalizeEmail(currentEmail);
        String normalizedNew = normalizeEmail(request.newEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedCurrent)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedCurrent));

        if (userRepository.existsByEmail(normalizedNew)) {
            throw new DuplicateResourceException("User", "email", normalizedNew);
        }

        if (!otpService.isOtpVerified(normalizedNew, OtpPurpose.CHANGE_EMAIL)) {
            throw new InvalidRequestException("New email address has not been verified with OTP.");
        }

        user.setEmail(normalizedNew);
        userRepository.save(user);

        otpService.consumeOtpVerifiedMarker(normalizedNew, OtpPurpose.CHANGE_EMAIL);
        log.info("[VelaWear/Auth] - CHANGE_EMAIL: from: {}, to: {}", normalizedCurrent, normalizedNew);
    }

    @Override
    @Transactional
    public void changePassword(String currentEmail, ChangePasswordRequest request) {
        String normalizedEmail = normalizeEmail(currentEmail);

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

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
        userRepository.save(user);

        log.info("[VelaWear/Auth] - CHANGE_PASSWORD: email: {}, hadPassword: {}", normalizedEmail, hasPassword);
    }
}
