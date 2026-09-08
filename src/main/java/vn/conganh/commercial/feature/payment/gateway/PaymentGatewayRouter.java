package vn.conganh.commercial.feature.payment.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.PaymentGatewayUnavailableException;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Component
public class PaymentGatewayRouter {

    private final Map<PaymentProvider, PaymentGateway> gatewayMap;

    public PaymentGatewayRouter(List<PaymentGateway> gateways) {
        this.gatewayMap = new EnumMap<>(PaymentProvider.class);
        for (PaymentGateway gateway : gateways) {
            this.gatewayMap.put(gateway.getProvider(), gateway);
        }
    }

    public PaymentGateway getGateway(PaymentProvider provider) {
        if (provider == null) {
            throw new InvalidRequestException("Payment provider must not be null");
        }
        PaymentGateway gateway = gatewayMap.get(provider);
        if (gateway == null) {
            throw new InvalidRequestException("Unsupported payment provider: " + provider);
        }
        if (!gateway.isAvailable()) {
            throw new PaymentGatewayUnavailableException("Payment gateway for " + provider + " is currently unavailable");
        }
        return gateway;
    }

    public PaymentGateway getGateway(String providerOrMethod) {
        if (providerOrMethod == null || providerOrMethod.isBlank()) {
            throw new InvalidRequestException("Payment method/provider must not be blank");
        }
        String normalized = providerOrMethod.trim().toUpperCase();
        if ("BANK_TRANSFER".equals(normalized)) {
            normalized = "SEPAY";
        }
        try {
            PaymentProvider provider = PaymentProvider.valueOf(normalized);
            return getGateway(provider);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown payment provider: " + providerOrMethod);
        }
    }
}
