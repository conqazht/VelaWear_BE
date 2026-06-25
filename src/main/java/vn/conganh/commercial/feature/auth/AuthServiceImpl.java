package vn.conganh.commercial.feature.auth;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public TokenResponse authenticate(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String email = authentication.getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = generateAccessToken(authentication, user.getId());
        String refreshToken = UUID.randomUUID().toString();

        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                refreshToken,
                Instant.now().plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS),
                null,
                null
        ));

        log.info("[VelaWear/Auth] - LOGIN: userId: {}, email: {}", user.getId(), email);

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessTokenExpiration()
        );
    }

    private String generateAccessToken(Authentication authentication, Long userId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plus(jwtProperties.accessTokenExpiration(), ChronoUnit.SECONDS);

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(authentication.getName())
                    .claim("userId", userId)
                    .claim("roles", roles)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .build();

            SecretKey key = new SecretKeySpec(
                    jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            JWSSigner signer = new MACSigner(key);

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS512), claims);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }
}
