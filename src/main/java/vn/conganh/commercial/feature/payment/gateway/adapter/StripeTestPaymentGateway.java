package vn.conganh.commercial.feature.payment.gateway.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.config.StripeProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentGateway;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeTestPaymentGateway implements PaymentGateway {

    private final StripeProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
    }

    @Override
    public PaymentInitiationResult initiatePayment(Order order, Payment payment) {
        Stripe.apiKey = properties.getSecretKey();

        long unitAmount = order.getFinalAmount() != null ? order.getFinalAmount().longValue() : 0L;
        String currency = properties.getCurrency() != null ? properties.getCurrency().toLowerCase() : "vnd";

        String successUrl = resolveUrl(properties.getSuccessUrl(), order);
        String cancelUrl = resolveUrl(properties.getCancelUrl(), order);

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .putMetadata("orderCode", order.getOrderCode())
                .putMetadata("orderId", String.valueOf(order.getId()))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(currency)
                                                .setUnitAmount(unitAmount)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Đơn hàng " + order.getOrderCode())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (order.getReservationExpiresAt() != null) {
            long expiresEpoch = order.getReservationExpiresAt().getEpochSecond();
            long minAllowedExpires = Instant.now().plus(Duration.ofMinutes(30)).getEpochSecond();
            if (expiresEpoch >= minAllowedExpires) {
                paramsBuilder.setExpiresAt(expiresEpoch);
            }
        }

        String actionUrl = null;
        String sessionId = null;
        Map<String, String> fields = new HashMap<>();

        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("stripe-session-" + order.getOrderCode())
                    .build();
            Session session = Session.create(paramsBuilder.build(), requestOptions);
            actionUrl = session.getUrl();
            sessionId = session.getId();
            fields.put("sessionId", sessionId);
        } catch (Exception e) {
            log.warn("Stripe API call failed (using mock/fallback URL for testing): {}", e.getMessage());
            sessionId = "cs_test_" + order.getOrderCode();
            actionUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", sessionId);
            fields.put("sessionId", sessionId);
        }

        String txCode = sessionId != null ? sessionId : "STRIPE-" + order.getOrderCode();

        return new PaymentInitiationResult(
                PaymentProvider.STRIPE,
                "STRIPE",
                actionUrl,
                Collections.unmodifiableMap(fields),
                txCode
        );
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature) {
        String webhookSecret = properties.getWebhookSecret();
        boolean isValid = false;
        String orderCode = params.get("orderCode");
        String transactionCode = params.get("transactionCode");
        String eventType = params.get("type");
        BigDecimal amount = null;

        if (rawBody != null && !rawBody.isBlank() && signature != null && !signature.isBlank() && webhookSecret != null && !webhookSecret.isBlank()) {
            try {
                Event event = Webhook.constructEvent(rawBody, signature, webhookSecret);
                isValid = true;
                eventType = event.getType();
                if (event.getDataObjectDeserializer().getObject().isPresent()) {
                    Object dataObj = event.getDataObjectDeserializer().getObject().get();
                    if (dataObj instanceof Session session) {
                        if (session.getMetadata() != null && session.getMetadata().containsKey("orderCode")) {
                            orderCode = session.getMetadata().get("orderCode");
                        }
                        transactionCode = session.getId();
                        if (session.getAmountTotal() != null) {
                            amount = BigDecimal.valueOf(session.getAmountTotal());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Stripe webhook verification failed: {}", e.getMessage());
                isValid = false;
            }
        } else if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("type")) {
                    eventType = root.get("type").asText();
                }
                if (root.has("data") && root.get("data").has("object")) {
                    JsonNode obj = root.get("data").get("object");
                    if (obj.has("metadata") && obj.get("metadata").has("orderCode")) {
                        orderCode = obj.get("metadata").get("orderCode").asText();
                    }
                    if (obj.has("id")) {
                        transactionCode = obj.get("id").asText();
                    }
                    if (obj.has("amount_total")) {
                        amount = BigDecimal.valueOf(obj.get("amount_total").asLong());
                    }
                }
                isValid = true;
            } catch (Exception ignored) {}
        } else {
            isValid = true;
        }

        boolean isSuccess = isValid && (eventType == null || "checkout.session.completed".equals(eventType) || "payment_intent.succeeded".equals(eventType));

        return PaymentCallbackResult.builder()
                .success(isSuccess)
                .provider(PaymentProvider.STRIPE)
                .orderCode(orderCode)
                .transactionCode(transactionCode != null ? transactionCode : "STRIPE-" + orderCode)
                .amount(amount)
                .message(isSuccess ? "Stripe payment success" : "Stripe payment failed")
                .responseCode(isSuccess ? "00" : "01")
                .rawResponse(rawBody != null ? rawBody : params.toString())
                .build();
    }

    private String resolveUrl(String template, Order order) {
        if (template == null) {
            return "";
        }
        return template.replace("{ORDER_CODE}", order.getOrderCode())
                .replace("{ORDER_ID}", String.valueOf(order.getId()));
    }
}
