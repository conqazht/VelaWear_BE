package vn.conganh.commercial.feature.emailoutbox;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;

@Component
public class CommerceEmailTemplateRenderer {

    public record RenderedEmail(String subject, String html) {}

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm 'ngày' dd/MM/yyyy", VIETNAMESE)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailNotificationProperties properties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    private final String orderCompletedTemplate;
    private final String orderCreatedTemplate;
    private final String paymentSucceededTemplate;
    private final String paymentFailedTemplate;

    public CommerceEmailTemplateRenderer(
            EmailNotificationProperties properties,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;

        this.orderCompletedTemplate = loadTemplate("templates/email/order-completed.html");
        this.orderCreatedTemplate = loadTemplate("templates/email/order-created.html");
        this.paymentSucceededTemplate = loadTemplate("templates/email/payment-succeeded.html");
        this.paymentFailedTemplate = loadTemplate("templates/email/payment-failed.html");
    }

    @Transactional(readOnly = true)
    public RenderedEmail render(EmailOutboxStore.Claim claim) {
        Order order = orderRepository.findById(claim.orderId())
                .orElseThrow(() -> new IllegalStateException("Order no longer exists: " + claim.orderId()));
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        String orderUrl = properties.storefrontBaseUrl()
                + "/profile/orders/"
                + UriUtils.encodePathSegment(order.getOrderCode(), StandardCharsets.UTF_8);

        Payment latestPayment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElse(null);

        return switch (claim.templateKey()) {
            case ORDER_CREATED -> renderOrderCreated(order, items, orderUrl, claim);
            case PAYMENT_SUCCEEDED -> renderPaymentSucceeded(order, latestPayment, orderUrl, claim);
            case PAYMENT_FAILED -> renderPaymentFailed(order, latestPayment, orderUrl, claim);
            case ORDER_COMPLETED -> renderOrderCompleted(order, items, orderUrl, claim);
        };
    }

    private RenderedEmail renderOrderCreated(Order order, List<OrderItem> items, String orderUrl, EmailOutboxStore.Claim claim) {
        String html = replacePlaceholders(orderCreatedTemplate, Map.ofEntries(
                Map.entry("customerName", escape(order.getReceiverName())),
                Map.entry("orderCode", escape(order.getOrderCode())),
                Map.entry("createdAt", escape(formatDateTime(order.getCreatedAt() != null ? order.getCreatedAt() : claim.createdAt()))),
                Map.entry("itemCount", String.valueOf(items.size())),
                Map.entry("itemRows", renderItemRows(items)),
                Map.entry("finalAmount", escape(formatMoney(order.getFinalAmount()))),
                Map.entry("paymentMethod", escape(formatPaymentMethod(order.getPaymentMethod()))),
                Map.entry("receiverName", escape(order.getReceiverName())),
                Map.entry("receiverAddress", escape(order.getReceiverAddress())),
                Map.entry("orderUrl", escape(orderUrl))));

        return new RenderedEmail(
                "[VelaWear] Đã nhận đơn hàng " + order.getOrderCode(),
                html);
    }

    private RenderedEmail renderPaymentSucceeded(Order order, Payment payment, String orderUrl, EmailOutboxStore.Claim claim) {
        String txCode = payment != null && payment.getTransactionCode() != null ? payment.getTransactionCode() : "VelaWear-" + order.getOrderCode();
        String provider = payment != null && payment.getProvider() != null ? payment.getProvider().name() : order.getPaymentMethod();
        Instant paidAt = payment != null && payment.getPaidAt() != null ? payment.getPaidAt() : claim.createdAt();

        String html = replacePlaceholders(paymentSucceededTemplate, Map.ofEntries(
                Map.entry("customerName", escape(order.getReceiverName())),
                Map.entry("orderCode", escape(order.getOrderCode())),
                Map.entry("paidAt", escape(formatDateTime(paidAt))),
                Map.entry("transactionCode", escape(txCode)),
                Map.entry("finalAmount", escape(formatMoney(order.getFinalAmount()))),
                Map.entry("paymentProvider", escape(provider)),
                Map.entry("orderUrl", escape(orderUrl))));

        return new RenderedEmail(
                "[VelaWear] Xác nhận thanh toán thành công đơn hàng " + order.getOrderCode(),
                html);
    }

    private RenderedEmail renderPaymentFailed(Order order, Payment payment, String orderUrl, EmailOutboxStore.Claim claim) {
        String provider = payment != null && payment.getProvider() != null ? payment.getProvider().name() : order.getPaymentMethod();
        String reason = "Giao dịch không thành công hoặc đã hết hạn thanh toán";

        String html = replacePlaceholders(paymentFailedTemplate, Map.ofEntries(
                Map.entry("customerName", escape(order.getReceiverName())),
                Map.entry("orderCode", escape(order.getOrderCode())),
                Map.entry("attemptedAt", escape(formatDateTime(claim.createdAt()))),
                Map.entry("failureReason", escape(reason)),
                Map.entry("finalAmount", escape(formatMoney(order.getFinalAmount()))),
                Map.entry("paymentProvider", escape(provider)),
                Map.entry("orderUrl", escape(orderUrl))));

        return new RenderedEmail(
                "[VelaWear] Thông báo thanh toán chưa thành công cho đơn hàng " + order.getOrderCode(),
                html);
    }

    private RenderedEmail renderOrderCompleted(Order order, List<OrderItem> items, String orderUrl, EmailOutboxStore.Claim claim) {
        String html = replacePlaceholders(orderCompletedTemplate, Map.ofEntries(
                Map.entry("customerName", escape(order.getReceiverName())),
                Map.entry("orderCode", escape(order.getOrderCode())),
                Map.entry("completedAt", escape(formatDateTime(claim.createdAt()))),
                Map.entry("itemCount", String.valueOf(items.size())),
                Map.entry("itemRows", renderItemRows(items)),
                Map.entry("finalAmount", escape(formatMoney(order.getFinalAmount()))),
                Map.entry("paymentMethod", escape(formatPaymentMethod(order.getPaymentMethod()))),
                Map.entry("receiverName", escape(order.getReceiverName())),
                Map.entry("receiverAddress", escape(order.getReceiverAddress())),
                Map.entry("orderUrl", escape(orderUrl))));

        return new RenderedEmail(
                "[VelaWear] Đơn hàng " + order.getOrderCode() + " đã được giao thành công",
                html);
    }

    private String replacePlaceholders(String source, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                throw new IllegalStateException("Unknown email placeholder: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String renderItemRows(List<OrderItem> items) {
        if (items.isEmpty()) {
            return """
                    <tr>
                      <td colspan="3" style="padding:18px 0;color:#777;font-size:14px;">
                        Không có sản phẩm nào
                      </td>
                    </tr>
                    """;
        }

        StringBuilder rows = new StringBuilder();
        for (OrderItem item : items) {
            String title = item.getProductName();
            String variant = item.getVariantName() != null && !item.getVariantName().isBlank()
                    ? item.getVariantName()
                    : "";
            String price = formatMoney(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

            rows.append("""
                    <tr>
                      <td style="padding:16px 0;border-bottom:1px solid #f0eee9;vertical-align:top;">
                        <strong style="color:#1c1a18;font-size:14px;display:block;">""");
            rows.append(escape(title));
            rows.append("""
                        </strong>
                        <span style="color:#77736e;font-size:13px;">""");
            rows.append(escape(variant));
            rows.append("""
                        </span>
                      </td>
                      <td align="center" style="padding:16px 8px;border-bottom:1px solid #f0eee9;vertical-align:top;color:#68645f;font-size:14px;">
                        x""");
            rows.append(item.getQuantity());
            rows.append("""
                      </td>
                      <td align="right" style="padding:16px 0;border-bottom:1px solid #f0eee9;vertical-align:top;font-weight:700;color:#1c1a18;font-size:14px;">
                        """);
            rows.append(escape(price));
            rows.append("""
                      </td>
                    </tr>
                    """);
        }
        return rows.toString();
    }

    private String formatDateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        NumberFormat numberFormat = NumberFormat.getInstance(VIETNAMESE);
        return numberFormat.format(amount) + " ₫";
    }

    private String formatPaymentMethod(String method) {
        if (method == null) {
            return "Chưa xác định";
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "COD" -> "Thanh toán khi nhận hàng (COD)";
            case "SEPAY", "BANK_TRANSFER" -> "Chuyển khoản ngân hàng (VietQR)";
            case "VNPAY" -> "Cổng thanh toán VNPay";
            case "MOMO" -> "Ví điện tử MoMo";
            case "STRIPE" -> "Thẻ quốc tế Visa/Mastercard (Stripe)";
            default -> method;
        };
    }

    private String escape(String input) {
        return input == null ? "" : HtmlUtils.htmlEscape(input);
    }

    private String loadTemplate(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load email template: " + path, exception);
        }
    }
}
