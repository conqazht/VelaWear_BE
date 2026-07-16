package vn.conganh.commercial.feature.auth.otp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    private long ttlSeconds = 300;
    private long proofTtlSeconds = 300;
}
