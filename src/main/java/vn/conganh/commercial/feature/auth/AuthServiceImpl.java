package vn.conganh.commercial.feature.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.exception.UnauthorizedException;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshToken;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserHasRole;
import vn.conganh.commercial.feature.user.UserHasRoleRepository;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.user.dto.UserResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final MacAlgorithm JWT_MAC_ALGORITHM = MacAlgorithm.HS512;
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHasRoleRepository userHasRoleRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public TokenResponse authenticate(LoginRequest request) {
        return authenticate(request, null, null);
    }

    @Override
    @Transactional
    public TokenResponse authenticate(LoginRequest request, String deviceInfo, String ipAddress) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String email = authentication.getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String accessToken = generateAccessToken(email, user.getId(), roles);
        String refreshToken = createRefreshToken(user, deviceInfo, ipAddress);

        log.info("[VelaWear/Auth] - LOGIN: userId: {}, email: {}", user.getId(), email);

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessTokenExpiration()
        );
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
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

        log.info("[VelaWear/Auth] - REGISTER: userId: {}", savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        validateRefreshJwt(request.refreshToken());
        RefreshToken existingToken = refreshTokenService.findValidRefreshToken(request.refreshToken());
        User user = existingToken.getUser();
        existingToken.setRevoked(true);

        List<String> roles = userHasRoleRepository.findRolesByUserId(user.getId()).stream()
                .map(role -> "ROLE_" + role.getName())
                .toList();
        String accessToken = generateAccessToken(user.getEmail(), user.getId(), roles);
        String refreshToken = createRefreshToken(user, existingToken.getDeviceInfo(), existingToken.getIpAddress());

        log.info("[VelaWear/Auth] - REFRESH_TOKEN: userId: {}", user.getId());

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        List<UserResponse.RoleSummaryResponse> roles = userHasRoleRepository.findRolesByUserId(user.getId()).stream()
                .map(UserResponse.RoleSummaryResponse::fromEntity)
                .toList();
        return UserResponse.fromEntity(user, roles);
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
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS);
        String refreshToken = generateRefreshToken(user, now, expiresAt);
        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                refreshToken,
                expiresAt,
                deviceInfo,
                ipAddress
        ));
        return refreshToken;
    }

    private String generateRefreshToken(User user, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("type", REFRESH_TOKEN_TYPE)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(JWT_MAC_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void validateRefreshJwt(String rawRefreshToken) {
        try {
            Jwt jwt = jwtDecoder.decode(rawRefreshToken);
            if (!REFRESH_TOKEN_TYPE.equals(jwt.getClaimAsString("type"))) {
                throw new UnauthorizedException("Refresh token type is invalid");
            }
        } catch (JwtException exception) {
            throw new UnauthorizedException("Refresh token is invalid");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
