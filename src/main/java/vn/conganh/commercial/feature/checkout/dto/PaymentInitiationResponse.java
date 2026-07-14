package vn.conganh.commercial.feature.checkout.dto;

import java.util.Map;

public record PaymentInitiationResponse(
        String provider,
        String method,
        String actionUrl,
        Map<String, String> fields
) {}
