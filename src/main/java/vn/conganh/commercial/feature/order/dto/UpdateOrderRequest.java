package vn.conganh.commercial.feature.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateOrderRequest(

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @DecimalMin(value = "0.00", message = "Shipping fee must be >= 0")
        BigDecimal shippingFee,

        @DecimalMin(value = "0.00", message = "Discount amount must be >= 0")
        BigDecimal discountAmount,

        @DecimalMin(value = "0.00", message = "Final amount must be >= 0")
        BigDecimal finalAmount,

        @Size(max = 150, message = "Receiver name must be at most 150 characters")
        String receiverName,

        @Size(max = 20, message = "Receiver phone must be at most 20 characters")
        String receiverPhone,

        @Size(max = 500, message = "Receiver address must be at most 500 characters")
        String receiverAddress,

        @Size(max = 30, message = "Payment method must be at most 30 characters")
        String paymentMethod,

        @Size(max = 30, message = "Payment status must be at most 30 characters")
        String paymentStatus
) {
}
