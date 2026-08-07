package vn.conganh.commercial.feature.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.SessionRevokedException;
import vn.conganh.commercial.exception.UnauthorizedException;

@Component
public class AuthTokenCodec {

    private static final MacAlgorithm JWT_MAC_ALGORITHM = MacAlgorithm.HS512;
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtEncoder accessJwtEncoder;
    private final JwtEncoder refreshJwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final JwtProperties jwtProperties;

    public AuthTokenCodec(
            @Qualifier("jwtEncoder") JwtEncoder accessJwtEncoder,
            @Qualifier("refreshJwtEncoder") JwtEncoder refreshJwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            JwtProperties jwtProperties) {
        this.accessJwtEncoder = accessJwtEncoder;
        this.refreshJwtEncoder = refreshJwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(
            String email,
            Long userId,
            long securityVersion,
            List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("securityVersion", securityVersion)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenExpiration(), ChronoUnit.SECONDS))
                .build();

        JwsHeader header = JwsHeader.with(JWT_MAC_ALGORITHM).build();
        return accessJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String generateRefreshToken(
            String email,
            Long userId,
            long securityVersion,
            Instant issuedAt,
            Instant expiresAt,
            String jti) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .subject(email)
                .claim("userId", userId)
                .claim("securityVersion", securityVersion)
                .claim("type", REFRESH_TOKEN_TYPE)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(JWT_MAC_ALGORITHM).build();
        return refreshJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt validateAndDecodeRefreshJwt(String rawRefreshToken) {
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

    public String requireJti(Jwt jwt) {
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw new UnauthorizedException("Refresh token id is invalid");
        }
        return jwt.getId();
    }

    public long requireSecurityVersion(Jwt jwt) {
        Object claim = jwt.getClaim("securityVersion");
        if (claim instanceof Number number && number.longValue() >= 0) {
            return number.longValue();
        }
        throw new SessionRevokedException();
    }
}
