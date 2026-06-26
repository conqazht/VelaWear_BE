package vn.conganh.commercial.feature.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.dto.PaymentResponse;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Payment - PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, orderRepository);
    }

    @Nested
    @DisplayName("Create payment")
    class CreatePayment {

        @Test
        @DisplayName("createPayment - tạo payment thành công khi order tồn tại")
        void createPayment_existingOrder_returnsPaymentResponse() {
            // Arrange
            Order order = order(10L, "VW-TEST-001");
            CreatePaymentRequest request = request(10L, PaymentStatus.SUCCESS);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment payment = invocation.getArgument(0);
                ReflectionTestUtils.setField(payment, "id", 1L);
                return payment;
            });

            // Act
            PaymentResponse response = paymentService.createPayment(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.orderId()).isEqualTo(10L);
            assertThat(response.orderCode()).isEqualTo("VW-TEST-001");
            assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("createPayment - không gọi save khi order không tồn tại")
        void createPayment_missingOrder_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreatePaymentRequest request = request(99L, PaymentStatus.SUCCESS);
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update payment")
    class UpdatePayment {

        @Test
        @DisplayName("updatePayment - cập nhật payment và đổi order khi orderId được truyền")
        void updatePayment_existingPaymentAndOrder_returnsUpdatedResponse() {
            // Arrange
            Payment payment = payment(1L, order(10L, "VW-TEST-001"));
            Order newOrder = order(20L, "VW-TEST-002");
            UpdatePaymentRequest request = new UpdatePaymentRequest(
                    20L,
                    PaymentProvider.MOMO,
                    "TXN-002",
                    BigDecimal.valueOf(200000),
                    PaymentStatus.REFUNDED,
                    Instant.parse("2026-01-02T00:00:00Z"));
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(orderRepository.findById(20L)).thenReturn(Optional.of(newOrder));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PaymentResponse response = paymentService.updatePayment(1L, request);

            // Assert
            assertThat(response.orderId()).isEqualTo(20L);
            assertThat(response.provider()).isEqualTo(PaymentProvider.MOMO);
            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        }
    }

    @Nested
    @DisplayName("Read payment")
    class ReadPayment {

        @Test
        @DisplayName("getPaymentById - ném ResourceNotFoundException khi không tìm thấy payment")
        void getPaymentById_missingPayment_throwsResourceNotFoundException() {
            // Arrange
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private CreatePaymentRequest request(Long orderId, PaymentStatus status) {
        return new CreatePaymentRequest(
                orderId,
                PaymentProvider.VNPAY,
                "TXN-001",
                BigDecimal.valueOf(100000),
                status,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private Payment payment(Long id, Order order) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", id);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setTransactionCode("TXN-001");
        payment.setAmount(BigDecimal.valueOf(100000));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.parse("2026-01-01T00:00:00Z"));
        return payment;
    }

    private Order order(Long id, String orderCode) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setOrderCode(orderCode);
        return order;
    }
}
