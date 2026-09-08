package vn.conganh.commercial.feature.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentEnvironmentProperties {
    private String environment = "sandbox";
    private SimulationProperties simulation = new SimulationProperties();

    @Data
    public static class SimulationProperties {
        private boolean enabled = true;
    }
}
