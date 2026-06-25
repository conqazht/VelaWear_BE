package vn.conganh.commercial.feature.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateOrderRequest(

        @NotNull(message = "User ID is required")
        Long userId,

        @NotBlank(message = "Order code is required")
        @Size(max = 50, message = "Order code must be at most 50 characters")
        String orderCode,

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @NotNull(message = "Subtotal is required")
        @DecimalMin(value = "0.00", message = "Subtotal must be >= 0")
        BigDecimal subtotal,

        @DecimalMin(value = "0.00", message = "Shipping fee must be >= 0")
        BigDecimal shippingFee,

        @DecimalMin(value = "0.00", message = "Discount amount must be >= 0")
        BigDecimal discountAmount,

        @NotNull(message = "Final amount is required")
        @DecimalMin(value = "0.00", message = "Final amount must be >= 0")
        BigDecimal finalAmount,

        @NotBlank(message = "Receiver name is required")
        @Size(max = 150, message = "Receiver name must be at most 150 characters")
        String receiverName,

        @NotBlank(message = "Receiver phone is required")
        @Size(max = 20, message = "Receiver phone must be at most 20 characters")
        String receiverPhone,

        @NotBlank(message = "Receiver address is required")
        @Size(max = 500, message = "Receiver address must be at most 500 characters")
        String receiverAddress,

        @NotBlank(message = "Payment method is required")
        @Size(max = 30, message = "Payment method must be at most 30 characters")
        String paymentMethod,

        @Size(max = 30, message = "Payment status must be at most 30 characters")
        String paymentStatus
) {
}
