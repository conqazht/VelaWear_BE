package vn.conganh.commercial.feature.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutPreviewRequest(
        @NotBlank @Size(max = 30) String paymentMethod,
        String couponCode
) {}
