package vn.conganh.commercial;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tools.jackson.databind.ObjectMapper;

public abstract class AuthenticatedIntegrationTest extends AbstractIntegrationTest {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtEncoder jwtEncoder;

    protected String adminToken() {
        return tokenWithRoles("admin@velawear.local", 1L, List.of("ROLE_ADMIN"));
    }

    protected String userToken() {
        return tokenWithRoles("user@velawear.local", 2L, List.of("ROLE_USER"));
    }

    protected String tokenWithRoles(String subject, Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
