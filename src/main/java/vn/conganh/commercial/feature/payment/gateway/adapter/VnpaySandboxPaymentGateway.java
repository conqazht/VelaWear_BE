package vn.conganh.commercial.feature.payment.gateway.adapter;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.config.VnPayProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentGateway;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Slf4j
@Component
@RequiredArgsConstructor
public class VnpaySandboxPaymentGateway implements PaymentGateway {

    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnPayProperties properties;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getTmnCode() != null && !properties.getTmnCode().isBlank()
                && properties.getHashSecret() != null && !properties.getHashSecret().isBlank();
    }

    @Override
    public PaymentInitiationResult initiatePayment(Order order, Payment payment) {
        Instant now = Instant.now();
        Instant expireTime = now.plus(Duration.ofMinutes(properties.getExpireMinutes()));
        if (order.getReservationExpiresAt() != null && order.getReservationExpiresAt().isBefore(expireTime)) {
            expireTime = order.getReservationExpiresAt();
        }

        BigDecimal rawAmount = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;
        long vnpAmount = rawAmount.multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", properties.getVersion());
        vnpParams.put("vnp_Command", properties.getCommand());
        vnpParams.put("vnp_TmnCode", properties.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", properties.getCurrCode());
        vnpParams.put("vnp_TxnRef", order.getOrderCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        vnpParams.put("vnp_OrderType", properties.getOrderType());
        vnpParams.put("vnp_Locale", properties.getLocale());
        vnpParams.put("vnp_ReturnUrl", properties.getReturnUrl());
        vnpParams.put("vnp_IpAddr", "127.0.0.1");
        vnpParams.put("vnp_CreateDate", VNP_DATE_FORMAT.format(now));
        vnpParams.put("vnp_ExpireDate", VNP_DATE_FORMAT.format(expireTime));

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                String encodedName = urlEncode(fieldName);
                String encodedValue = urlEncode(fieldValue);

                hashData.append(encodedName).append('=').append(encodedValue);
                query.append(encodedName).append('=').append(encodedValue);

                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }

        String secureHash = hmacSHA512(properties.getHashSecret(), hashData.toString());
        String paymentUrl = properties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
        String txCode = "VNPAY-" + order.getOrderCode();

        return new PaymentInitiationResult(
                PaymentProvider.VNPAY,
                "VNPAY",
                paymentUrl,
                Collections.unmodifiableMap(vnpParams),
                txCode
        );
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature) {
        String secureHash = signature != null && !signature.isBlank()
                ? signature
                : params.get("vnp_SecureHash");

        Map<String, String> vnpParams = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("vnp_")
                    && !"vnp_SecureHash".equals(entry.getKey())
                    && !"vnp_SecureHashType".equals(entry.getKey())) {
                vnpParams.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(urlEncode(fieldName)).append('=').append(urlEncode(fieldValue));
                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                }
            }
        }

        String calculatedHash = hmacSHA512(properties.getHashSecret(), hashData.toString());
        boolean isValidSignature = calculatedHash.equalsIgnoreCase(secureHash);

        String orderCode = params.get("vnp_TxnRef");
        String vnpTransactionNo = params.get("vnp_TransactionNo");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");

        boolean isSuccess = isValidSignature
                && "00".equals(responseCode)
                && ("00".equals(transactionStatus) || transactionStatus == null);

        BigDecimal amount = null;
        if (params.get("vnp_Amount") != null) {
            try {
                amount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
            } catch (Exception ignored) {}
        }

        return PaymentCallbackResult.builder()
                .success(isSuccess)
                .provider(PaymentProvider.VNPAY)
                .orderCode(orderCode)
                .transactionCode(vnpTransactionNo != null && !vnpTransactionNo.isBlank() ? vnpTransactionNo : "VNPAY-" + orderCode)
                .amount(amount)
                .message(isSuccess ? "VNPay payment success" : (isValidSignature ? "VNPay payment failed: " + responseCode : "Invalid VNPay signature"))
                .responseCode(isValidSignature ? (responseCode != null ? responseCode : "00") : "97")
                .rawResponse(rawBody != null ? rawBody : params.toString())
                .build();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate VNPay HMAC SHA512 signature", e);
            throw new IllegalStateException("Failed to calculate VNPay signature", e);
        }
    }
}
