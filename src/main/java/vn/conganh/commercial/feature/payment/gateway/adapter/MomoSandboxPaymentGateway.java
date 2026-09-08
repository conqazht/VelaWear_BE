package vn.conganh.commercial.feature.payment.gateway.adapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.config.MomoProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentGateway;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Slf4j
@Component
@RequiredArgsConstructor
public class MomoSandboxPaymentGateway implements PaymentGateway {

    private final MomoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MOMO;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getPartnerCode() != null && !properties.getPartnerCode().isBlank()
                && properties.getAccessKey() != null && !properties.getAccessKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
    }

    @Override
    public PaymentInitiationResult initiatePayment(Order order, Payment payment) {
        String requestId = UUID.randomUUID().toString();
        String orderId = order.getOrderCode();
        long amount = order.getFinalAmount() != null ? order.getFinalAmount().longValue() : 0L;
        String orderInfo = "Thanh toan don hang " + orderId;
        String extraData = "";
        String requestType = properties.getRequestType();

        String rawSignature = "accessKey=" + properties.getAccessKey()
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + properties.getIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + properties.getPartnerCode()
                + "&redirectUrl=" + properties.getReturnUrl()
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        String signature = hmacSHA256(properties.getSecretKey(), rawSignature);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", properties.getPartnerCode());
        requestBody.put("partnerName", "Vela Wear");
        requestBody.put("storeId", "VelaWearStore");
        requestBody.put("requestId", requestId);
        requestBody.put("amount", amount);
        requestBody.put("orderId", orderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", properties.getReturnUrl());
        requestBody.put("ipnUrl", properties.getIpnUrl());
        requestBody.put("lang", "vi");
        requestBody.put("extraData", extraData);
        requestBody.put("requestType", requestType);
        requestBody.put("signature", signature);

        String actionUrl = null;
        Map<String, String> responseFields = new HashMap<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getEndpoint(),
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("payUrl")) {
                    actionUrl = root.get("payUrl").asText();
                }
                if (root.has("qrCodeUrl")) {
                    responseFields.put("qrCodeUrl", root.get("qrCodeUrl").asText());
                }
                if (root.has("deeplink")) {
                    responseFields.put("deeplink", root.get("deeplink").asText());
                }
            }
        } catch (Exception e) {
            log.warn("Could not call remote MoMo sandbox API, using fallback redirect: {}", e.getMessage());
            actionUrl = properties.getReturnUrl() + "?orderId=" + orderId + "&resultCode=0";
        }

        if (actionUrl == null || actionUrl.isBlank()) {
            actionUrl = properties.getReturnUrl() + "?orderId=" + orderId;
        }

        String txCode = "MOMO-" + orderId;
        return new PaymentInitiationResult(
                PaymentProvider.MOMO,
                "MOMO",
                actionUrl,
                Collections.unmodifiableMap(responseFields),
                txCode
        );
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature) {
        String accessKey = params.getOrDefault("accessKey", properties.getAccessKey());
        String amount = params.getOrDefault("amount", "0");
        String extraData = params.getOrDefault("extraData", "");
        String message = params.getOrDefault("message", "");
        String orderId = params.getOrDefault("orderId", params.get("orderCode"));
        String orderInfo = params.getOrDefault("orderInfo", "");
        String orderType = params.getOrDefault("orderType", "momo_wallet");
        String partnerCode = params.getOrDefault("partnerCode", properties.getPartnerCode());
        String payType = params.getOrDefault("payType", "");
        String requestId = params.getOrDefault("requestId", "");
        String responseTime = params.getOrDefault("responseTime", "");
        String resultCode = params.getOrDefault("resultCode", "-1");
        String transId = params.getOrDefault("transId", "MOMO-" + orderId);

        String suppliedSignature = signature != null && !signature.isBlank()
                ? signature
                : params.get("signature");

        boolean isValidSignature = false;
        if (properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            String rawSignature = "accessKey=" + accessKey
                    + "&amount=" + amount
                    + "&extraData=" + extraData
                    + "&message=" + message
                    + "&orderId=" + orderId
                    + "&orderInfo=" + orderInfo
                    + "&orderType=" + orderType
                    + "&partnerCode=" + partnerCode
                    + "&payType=" + payType
                    + "&requestId=" + requestId
                    + "&responseTime=" + responseTime
                    + "&resultCode=" + resultCode
                    + "&transId=" + transId;

            String calculated = hmacSHA256(properties.getSecretKey(), rawSignature);
            isValidSignature = calculated.equalsIgnoreCase(suppliedSignature);
        }

        boolean isSuccess = ("0".equals(resultCode) || "9000".equals(resultCode)) && (isValidSignature || properties.getSecretKey().isBlank());

        BigDecimal parsedAmount = null;
        try {
            parsedAmount = new BigDecimal(amount);
        } catch (Exception ignored) {}

        return PaymentCallbackResult.builder()
                .success(isSuccess)
                .provider(PaymentProvider.MOMO)
                .orderCode(orderId)
                .transactionCode(transId)
                .amount(parsedAmount)
                .message(isSuccess ? "MoMo payment success" : "MoMo payment result: " + message)
                .responseCode(resultCode)
                .rawResponse(rawBody != null ? rawBody : params.toString())
                .build();
    }

    private String hmacSHA256(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MoMo HMAC SHA256 signature", e);
            throw new IllegalStateException("Failed to calculate MoMo signature", e);
        }
    }
}
