package vn.conganh.commercial.feature.checkout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutPreviewResponse(
        Instant serverTime,
        String pricingFingerprint,
        BigDecimal subtotal,
        BigDecimal couponEligibleSubtotal,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        List<CheckoutPreviewItemResponse> items
) {}
