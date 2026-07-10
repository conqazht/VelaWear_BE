package vn.conganh.commercial.feature.checkout.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CheckoutRequest(
        @NotBlank(message = "Receiver name is required")
        @Size(max = 150)
        String receiverName,

        @NotBlank(message = "Receiver phone is required")
        @Size(max = 20)
        String receiverPhone,

        @NotBlank(message = "Receiver address is required")
        @Size(max = 500)
        String receiverAddress,

        @NotBlank(message = "Payment method is required")
        @Size(max = 30)
        String paymentMethod,

        @DecimalMin(value = "0.00")
        BigDecimal shippingFee,

        String couponCode
) {}
