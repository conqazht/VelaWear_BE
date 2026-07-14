package vn.conganh.commercial.feature.payment.sepay;

import java.util.Map;

public record SePayCheckoutForm(String actionUrl, Map<String, String> fields) {}
