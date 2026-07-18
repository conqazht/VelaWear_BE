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

@Component
public class OrderCompletedEmailTemplateRenderer {

    private static final String TEMPLATE_PATH = "templates/email/order-completed.html";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter COMPLETED_AT_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm 'ngày' dd/MM/yyyy", VIETNAMESE)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailNotificationProperties properties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final String template;

    public OrderCompletedEmailTemplateRenderer(
            EmailNotificationProperties properties,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.template = loadTemplate();
    }

    @Transactional(readOnly = true)
    public RenderedEmail render(EmailOutboxStore.Claim claim) {
        if (claim.templateKey() != EmailTemplateKey.ORDER_COMPLETED) {
            throw new IllegalArgumentException("Unsupported email template: " + claim.templateKey());
        }

        Order order = orderRepository.findById(claim.orderId())
                .orElseThrow(() -> new IllegalStateException("Order no longer exists: " + claim.orderId()));
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        String orderUrl = properties.storefrontBaseUrl()
                + "/profile/orders/"
                + UriUtils.encodePathSegment(order.getOrderCode(), StandardCharsets.UTF_8);

        String html = replacePlaceholders(template, Map.ofEntries(
                Map.entry("customerName", escape(order.getReceiverName())),
                Map.entry("orderCode", escape(order.getOrderCode())),
                Map.entry("completedAt", escape(formatCompletedAt(claim.createdAt()))),
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
                throw new IllegalStateException("Unknown order completed email placeholder: " + key);
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
                        Không có thông tin sản phẩm.
                      </td>
                    </tr>
                    """;
        }

        StringBuilder rows = new StringBuilder();
        for (OrderItem item : items) {
            String variant = item.getVariantName() == null || item.getVariantName().isBlank()
                    ? ""
                    : "<br/><span style=\"color:#777;font-size:12px;\">"
                            + escape(item.getVariantName()) + "</span>";
            rows.append("""
                    <tr>
                      <td style="padding:18px 8px 18px 0;border-top:1px solid #ececec;vertical-align:top;">
                        <span style="font-size:14px;font-weight:600;color:#1c1a18;">%s</span>%s
                      </td>
                      <td style="padding:18px 8px;border-top:1px solid #ececec;text-align:center;vertical-align:top;color:#555;font-size:14px;">
                        ×%d
                      </td>
                      <td style="padding:18px 0 18px 8px;border-top:1px solid #ececec;text-align:right;vertical-align:top;color:#1c1a18;font-size:14px;font-weight:600;">
                        %s
                      </td>
                    </tr>
                    """.formatted(
                    escape(item.getProductName()),
                    variant,
                    item.getQuantity(),
                    escape(formatMoney(item.getSubtotal()))));
        }
        return rows.toString();
    }

    private String formatCompletedAt(Instant completedAt) {
        return COMPLETED_AT_FORMATTER.format(completedAt);
    }

    private String formatMoney(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(VIETNAMESE);
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(amount);
    }

    private String formatPaymentMethod(String paymentMethod) {
        return switch (paymentMethod) {
            case "COD" -> "Thanh toán khi nhận hàng";
            case "SEPAY", "BANK_TRANSFER" -> "Chuyển khoản ngân hàng";
            case "VNPAY" -> "VNPay";
            case "MOMO" -> "MoMo";
            default -> paymentMethod;
        };
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private String loadTemplate() {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load email template " + TEMPLATE_PATH, exception);
        }
    }

    public record RenderedEmail(String subject, String html) {
    }
}
