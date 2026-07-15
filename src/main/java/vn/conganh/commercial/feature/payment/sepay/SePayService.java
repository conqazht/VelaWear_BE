package vn.conganh.commercial.feature.payment.sepay;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.PaymentGatewayUnavailableException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransaction;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.PaymentTransactionStatus;
import vn.conganh.commercial.feature.checkout.OrderResourceLifecycleService;

@RequiredArgsConstructor
@Service
public class SePayService {

    private static final String PRODUCTION_CHECKOUT_URL = "https://pay.sepay.vn/v1/checkout/init";

    private final SePayProperties properties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final OrderResourceLifecycleService resourceLifecycleService;

    public SePayCheckoutForm createCheckoutForm(Order order) {
        validateProductionConfiguration();
        String amount = toVndAmount(order.getFinalAmount());

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", amount);
        fields.put("merchant", properties.getMerchantId());
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "Thanh toan don hang " + order.getOrderCode());
        fields.put("order_invoice_number", order.getOrderCode());
        fields.put("payment_method", "BANK_TRANSFER");
        putCallback(fields, "success_url", properties.getSuccessUrl(), order.getOrderCode());
        putCallback(fields, "error_url", properties.getErrorUrl(), order.getOrderCode());
        putCallback(fields, "cancel_url", properties.getCancelUrl(), order.getOrderCode());
        fields.put("signature", sign(fields));

        return new SePayCheckoutForm(properties.getCheckoutUrl(), Collections.unmodifiableMap(fields));
    }

    public boolean hasValidSecret(String suppliedSecret) {
        if (!properties.isEnabled() || isBlank(suppliedSecret) || isBlank(properties.getSecretKey())) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.getSecretKey().getBytes(UTF_8),
                suppliedSecret.getBytes(UTF_8));
    }

    @Transactional
    public void handleIpn(SePayIpnRequest request) {
        validateIpn(request);
        if (!"ORDER_PAID".equals(request.notification_type())) {
            return;
        }

        String invoiceNumber = request.order().order_invoice_number();
        Order order = orderRepository.findWithLockByOrderCode(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", invoiceNumber));
        Payment payment = paymentRepository.findWithLockByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", order.getId()));

        if (payment.getProvider() != PaymentProvider.SEPAY) {
            throw new InvalidRequestException("Payment provider does not match SePay IPN");
        }
        validateAmountsAndStatuses(order, request);

        String transactionCode = request.transaction().transaction_id();
        PaymentTransaction existingTransaction = transactionRepository.findByTransactionCode(transactionCode)
                .orElse(null);
        if (existingTransaction != null) {
            if (existingTransaction.getPayment().getId().equals(payment.getId())) {
                return;
            }
            throw new InvalidRequestException("SePay transaction is already linked to another payment");
        }

        boolean additionalCapturedPayment = payment.getTransactionCode() != null
                || payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                || "PAID".equals(order.getPaymentStatus())
                || "REFUND_PENDING".equals(order.getPaymentStatus());

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setTransactionCode(transactionCode);
        transaction.setStatus(additionalCapturedPayment
                ? PaymentTransactionStatus.REFUND_PENDING
                : PaymentTransactionStatus.SUCCESS);
        transaction.setGatewayResponse(objectMapper.writeValueAsString(request));
        transactionRepository.save(transaction);

        if (additionalCapturedPayment) {
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            order.setPaymentStatus("REFUND_PENDING");
            paymentRepository.save(payment);
            orderRepository.save(order);
            return;
        }

        boolean accepted = resourceLifecycleService.confirmLockedOrder(order);
        payment.setTransactionCode(transactionCode);
        payment.setPaidAt(Instant.now());
        if (accepted) {
            payment.setStatus(PaymentStatus.SUCCESS);
            order.setPaymentStatus("PAID");
        } else {
            transaction.setStatus(PaymentTransactionStatus.REFUND_PENDING);
            transactionRepository.save(transaction);
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            order.setPaymentStatus("REFUND_PENDING");
        }
        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    private void validateProductionConfiguration() {
        if (!properties.isEnabled()) {
            throw new PaymentGatewayUnavailableException("SePay payment is disabled");
        }
        if (!"production".equalsIgnoreCase(properties.getEnvironment())
                || !PRODUCTION_CHECKOUT_URL.equals(properties.getCheckoutUrl())) {
            throw new PaymentGatewayUnavailableException("SePay production configuration is invalid");
        }
        if (isBlank(properties.getMerchantId()) || isBlank(properties.getSecretKey())) {
            throw new PaymentGatewayUnavailableException("SePay credentials are not configured");
        }
        if (!properties.getMerchantId().startsWith("SP-LIVE-")
                || !properties.getSecretKey().startsWith("spsk_live_")) {
            throw new PaymentGatewayUnavailableException("SePay production credentials are required");
        }
    }

    private void validateIpn(SePayIpnRequest request) {
        if (request == null || isBlank(request.notification_type())) {
            throw new InvalidRequestException("Invalid SePay IPN payload");
        }
        if (!"ORDER_PAID".equals(request.notification_type())) {
            return;
        }
        if (request.order() == null || request.transaction() == null
                || isBlank(request.order().order_invoice_number())
                || isBlank(request.transaction().transaction_id())) {
            throw new InvalidRequestException("Incomplete SePay IPN payload");
        }
    }

    private void validateAmountsAndStatuses(Order order, SePayIpnRequest request) {
        SePayIpnRequest.OrderData sePayOrder = request.order();
        SePayIpnRequest.TransactionData transaction = request.transaction();
        BigDecimal expectedAmount = order.getFinalAmount();

        if (!"CAPTURED".equals(sePayOrder.order_status())
                || !"APPROVED".equals(transaction.transaction_status())
                || !"PAYMENT".equals(transaction.transaction_type())) {
            throw new InvalidRequestException("SePay IPN does not contain a completed payment");
        }
        if (!"VND".equals(sePayOrder.order_currency())
                || !"VND".equals(transaction.transaction_currency())) {
            throw new InvalidRequestException("SePay IPN currency mismatch");
        }
        if (sePayOrder.order_amount() == null || transaction.transaction_amount() == null
                || expectedAmount.compareTo(sePayOrder.order_amount()) != 0
                || expectedAmount.compareTo(transaction.transaction_amount()) != 0) {
            throw new InvalidRequestException("SePay IPN amount mismatch");
        }
    }

    private String sign(Map<String, String> fields) {
        String signedString = fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecretKey().getBytes(UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signedString.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign SePay checkout", exception);
        }
    }

    private String toVndAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("SePay amount must be greater than zero");
        }
        try {
            return amount.toBigIntegerExact().toString();
        } catch (ArithmeticException exception) {
            throw new InvalidRequestException("SePay only supports whole VND amounts");
        }
    }

    private void putCallback(Map<String, String> fields, String field, String url, String orderCode) {
        if (!isBlank(url)) {
            String separator = url.contains("?") ? "&" : "?";
            fields.put(field, url + separator + "orderCode=" + orderCode);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
