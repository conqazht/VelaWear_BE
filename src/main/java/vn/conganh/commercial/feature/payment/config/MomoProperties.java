package vn.conganh.commercial.feature.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.momo")
public class MomoProperties {
    private boolean enabled = true;
    private String partnerCode = "";
    private String accessKey = "";
    private String secretKey = "";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private String returnUrl = "http://localhost:3000/payment/momo/return";
    private String ipnUrl = "http://localhost:8080/api/v1/payments/momo/ipn";
    private String requestType = "captureWallet";
}
