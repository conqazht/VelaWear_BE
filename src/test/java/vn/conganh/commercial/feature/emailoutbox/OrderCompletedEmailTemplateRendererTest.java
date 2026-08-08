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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompletedEmailTemplateRenderer")
class OrderCompletedEmailTemplateRendererTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private OrderCompletedEmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailNotificationProperties properties = new EmailNotificationProperties(
                "https://shop.velawear.test/",
                new EmailNotificationProperties.Outbox(
                        false,
                        10_000,
                        10,
                        3,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        Duration.ofHours(1)));
        renderer = new OrderCompletedEmailTemplateRenderer(
                properties,
                orderRepository,
                orderItemRepository);
    }

    @Test
    @DisplayName("render produces escaped VelaWear HTML and a review CTA")
    void render_orderCompleted_buildsSafeHtml() {
        Order order = order();
        OrderItem item = item(order);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));
        Claim claim = new Claim(
                100L,
                10L,
                "customer@example.com",
                EmailTemplateKey.ORDER_COMPLETED,
                1,
                Instant.parse("2026-07-16T12:30:00Z"),
                UUID.randomUUID());

        OrderCompletedEmailTemplateRenderer.RenderedEmail email = renderer.render(claim);

        assertThat(email.subject()).isEqualTo("[VelaWear] Đơn hàng ORD-10 đã được giao thành công");
        assertThat(email.html())
                .contains("VELAWEAR")
                .contains("https://shop.velawear.test/profile/orders/ORD-10")
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
                .contains("Áo khoác &lt;đẹp&gt;")
                .contains("Xem đơn hàng &amp; đánh giá sản phẩm")
                .doesNotContain("<script>")
                .doesNotContain("Nike")
                .doesNotContain("nike")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("render does not treat placeholder-like customer data as template syntax")
    void render_customerDataContainingBraces_isNotProcessedAgain() {
        Order order = order();
        order.setReceiverName("Khách {{orderCode}}");
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item(order)));
        Claim claim = new Claim(
                100L,
                10L,
                "customer@example.com",
                EmailTemplateKey.ORDER_COMPLETED,
                1,
                Instant.parse("2026-07-16T12:30:00Z"),
                UUID.randomUUID());

        OrderCompletedEmailTemplateRenderer.RenderedEmail email = renderer.render(claim);

        assertThat(email.html())
                .contains("Khách {{orderCode}}")
                .doesNotContain("Khách ORD-10");
    }

    private Order order() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setOrderCode("ORD-10");
        order.setStatus("COMPLETED");
        order.setSubtotal(BigDecimal.valueOf(250000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(250000));
        order.setReceiverName("<script>alert('x')</script>");
        order.setReceiverPhone("0900000000");
        order.setReceiverAddress("123 & 456 Test");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("PAID");
        return order;
    }

    private OrderItem item(Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Áo khoác <đẹp>");
        item.setVariantName("Đen / L");
        item.setSku("SKU-1");
        item.setPrice(BigDecimal.valueOf(250000));
        item.setListPrice(BigDecimal.valueOf(250000));
        item.setQuantity(1);
        item.setSubtotal(BigDecimal.valueOf(250000));
        item.setStatus("CONFIRMED");
        return item;
    }
}
