package vn.conganh.commercial.feature.emailoutbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.emailoutbox.EmailOutboxStore.Claim;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommerceEmailTemplateRenderer Unit Test")
class CommerceEmailTemplateRendererTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private CommerceEmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailNotificationProperties properties = new EmailNotificationProperties(
                "http://localhost:3000",
                new EmailNotificationProperties.Outbox(
                        false,
                        10_000,
                        10,
                        3,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        Duration.ofHours(1)));

        renderer = new CommerceEmailTemplateRenderer(
                properties,
                orderRepository,
                orderItemRepository,
                paymentRepository);
    }

    @Test
    @DisplayName("renderPaymentSucceeded preserves UTF-8 Vietnamese diacritics and escapes HTML tags")
    void renderPaymentSucceeded_preservesVietnameseDiacriticsAndEscapesHtml() {
        Order order = createOrder(1L, "SEPAY-12345", "Lê Văn SePay <buyer>");
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(BigDecimal.valueOf(320000));
        payment.setTransactionCode("SEPAY-TX-12345");
        payment.setPaidAt(Instant.parse("2026-09-01T10:00:00Z"));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(payment));

        Claim claim = new Claim(
                10L,
                1L,
                "buyer@example.com",
                EmailTemplateKey.PAYMENT_SUCCEEDED,
                1,
                Instant.parse("2026-09-01T10:00:00Z"),
                UUID.randomUUID());

        CommerceEmailTemplateRenderer.RenderedEmail rendered = renderer.render(claim);

        assertThat(rendered.subject()).isEqualTo("[VelaWear] Xác nhận thanh toán thành công đơn hàng SEPAY-12345");
        assertThat(rendered.html())
                .contains("Lê Văn SePay")
                .contains("&lt;buyer&gt;")
                .doesNotContain("<buyer>")
                .doesNotContain("L&ecirc;");
    }

    @Test
    @DisplayName("renderPaymentSucceeded with Stripe provider formats details correctly")
    void renderPaymentSucceeded_stripeProvider_formatsCorrectly() {
        Order order = createOrder(2L, "STRIPE-67890", "Trương Văn Stripe");
        order.setPaymentMethod("STRIPE");

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 200L);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(BigDecimal.valueOf(500000));
        payment.setTransactionCode("cs_test_67890");
        payment.setPaidAt(Instant.parse("2026-09-01T10:05:00Z"));

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(payment));

        Claim claim = new Claim(
                20L,
                2L,
                "stripe.buyer@example.com",
                EmailTemplateKey.PAYMENT_SUCCEEDED,
                1,
                Instant.parse("2026-09-01T10:05:00Z"),
                UUID.randomUUID());

        CommerceEmailTemplateRenderer.RenderedEmail rendered = renderer.render(claim);

        assertThat(rendered.subject()).contains("STRIPE-67890");
        assertThat(rendered.html())
                .contains("Trương Văn Stripe")
                .contains("STRIPE")
                .contains("cs_test_67890");
    }

    @Test
    @DisplayName("renderPaymentFailed renders failure message and customer details")
    void renderPaymentFailed_rendersProperly() {
        Order order = createOrder(3L, "FAIL-11111", "Đỗ Thị Thất Bại");
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(3L)).thenReturn(List.of());
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.empty());

        Claim claim = new Claim(
                30L,
                3L,
                "fail@example.com",
                EmailTemplateKey.PAYMENT_FAILED,
                1,
                Instant.parse("2026-09-01T10:10:00Z"),
                UUID.randomUUID());

        CommerceEmailTemplateRenderer.RenderedEmail rendered = renderer.render(claim);

        assertThat(rendered.subject()).isEqualTo("[VelaWear] Thông báo thanh toán chưa thành công cho đơn hàng FAIL-11111");
        assertThat(rendered.html())
                .contains("Đỗ Thị Thất Bại")
                .contains("FAIL-11111");
    }

    @Test
    @DisplayName("renderOrderCreated renders items, customer info and formatted payment method")
    void renderOrderCreated_rendersItemsAndDetails() {
        Order order = createOrder(4L, "ORD-22222", "Nguyễn Văn Đặt Hàng");
        order.setPaymentMethod("STRIPE");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Áo sơ mi lụa");
        item.setVariantName("Trắng / M");
        item.setPrice(BigDecimal.valueOf(200000));
        item.setQuantity(2);

        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(4L)).thenReturn(List.of(item));

        Claim claim = new Claim(
                40L,
                4L,
                "created@example.com",
                EmailTemplateKey.ORDER_CREATED,
                1,
                Instant.parse("2026-09-01T10:15:00Z"),
                UUID.randomUUID());

        CommerceEmailTemplateRenderer.RenderedEmail rendered = renderer.render(claim);

        assertThat(rendered.subject()).isEqualTo("[VelaWear] Đã nhận đơn hàng ORD-22222");
        assertThat(rendered.html())
                .contains("Nguyễn Văn Đặt Hàng")
                .contains("Áo sơ mi lụa")
                .contains("Trắng / M")
                .contains("Thẻ quốc tế Visa/Mastercard (Stripe)");
    }

    private Order createOrder(Long id, String orderCode, String receiverName) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setOrderCode(orderCode);
        order.setStatus("PENDING");
        order.setPaymentStatus("UNPAID");
        order.setPaymentMethod("SEPAY");
        order.setSubtotal(BigDecimal.valueOf(320000));
        order.setFinalAmount(BigDecimal.valueOf(320000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setReceiverName(receiverName);
        order.setReceiverPhone("0987654321");
        order.setReceiverAddress("123 Test Street");
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-09-01T09:00:00Z"));
        return order;
    }
}
