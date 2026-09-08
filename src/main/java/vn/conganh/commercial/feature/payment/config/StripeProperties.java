package vn.conganh.commercial.feature.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.stripe")
public class StripeProperties {
    private boolean enabled = true;
    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "http://localhost:3000/payment/stripe/success?session_id={CHECKOUT_SESSION_ID}&order_code={ORDER_CODE}";
    private String cancelUrl = "http://localhost:3000/payment/stripe/cancel?order_code={ORDER_CODE}";
    private String currency = "vnd";
}
