package vn.conganh.commercial.feature.payment.gateway;

import java.util.Collections;
import java.util.Map;
import vn.conganh.commercial.feature.checkout.dto.PaymentInitiationResponse;
import vn.conganh.commercial.util.constant.PaymentProvider;

public record PaymentInitiationResult(
        PaymentProvider provider,
        String paymentMethod,
        String actionUrl,
        Map<String, String> fields,
        String transactionCode
) {
    public PaymentInitiationResponse toResponse() {
        return new PaymentInitiationResponse(
                provider.name(),
                paymentMethod,
                actionUrl,
                fields != null ? fields : Collections.emptyMap()
        );
    }
}
