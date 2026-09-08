package vn.conganh.commercial.feature.payment.gateway;

import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.emailoutbox.CommerceEmailOutboxService;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransaction;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.PaymentTransactionStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CommerceEmailOutboxService commerceEmailOutboxService;

    @Transactional
    public PaymentCallbackResult processCallback(PaymentCallbackResult callbackResult) {
        if (callbackResult == null || callbackResult.orderCode() == null || callbackResult.orderCode().isBlank()) {
            log.warn("event=payment_callback outcome=ignored reason=missing_order_code");
            return callbackResult;
        }

        String orderCode = callbackResult.orderCode();
        Order order = orderRepository.findWithLockByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", orderCode));

        Payment payment = paymentRepository.findWithLockByOrderId(order.getId())
                .orElseGet(() -> {
                    Payment newPayment = new Payment();
                    newPayment.setOrder(order);
                    newPayment.setProvider(callbackResult.provider());
                    newPayment.setAmount(order.getFinalAmount());
                    newPayment.setStatus(PaymentStatus.PENDING);
                    return paymentRepository.save(newPayment);
                });

        String txCode = callbackResult.transactionCode();

        // 1. Idempotency check on terminal status
        if (payment.getStatus() == PaymentStatus.SUCCESS && callbackResult.success()) {
            log.info("event=payment_callback outcome=idempotent_success orderCode={} txCode={}", orderCode, txCode);
            return callbackResult;
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS && !callbackResult.success()) {
            log.warn("event=payment_callback outcome=conflicting_status orderCode={} currentStatus=SUCCESS callbackStatus=FAILED", orderCode);
            // Record audit without reverting successful payment
            recordTransaction(payment, txCode, PaymentTransactionStatus.FAILED, callbackResult.rawResponse());
            return callbackResult;
        }

        // 2. Record Payment Transaction Audit
        PaymentTransactionStatus txStatus = callbackResult.success()
                ? PaymentTransactionStatus.SUCCESS
                : PaymentTransactionStatus.FAILED;
        recordTransaction(payment, txCode, txStatus, callbackResult.rawResponse());

        // 3. Update Order and Payment state
        if (callbackResult.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(Instant.now());
            if (txCode != null && !txCode.isBlank()) {
                payment.setTransactionCode(txCode);
            }
            order.setPaymentStatus("PAID");
            orderRepository.save(order);
            paymentRepository.save(payment);

            commerceEmailOutboxService.enqueuePaymentSucceeded(order);
            log.info("event=payment_callback outcome=payment_success orderCode={} provider={}", orderCode, callbackResult.provider());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            order.setPaymentStatus("FAILED");
            orderRepository.save(order);
            paymentRepository.save(payment);

            commerceEmailOutboxService.enqueuePaymentFailed(order);
            log.info("event=payment_callback outcome=payment_failed orderCode={} provider={}", orderCode, callbackResult.provider());
        }

        return callbackResult;
    }

    private void recordTransaction(Payment payment, String transactionCode, PaymentTransactionStatus status, String rawResponse) {
        if (transactionCode != null && !transactionCode.isBlank()) {
            if (paymentTransactionRepository.findByTransactionCode(transactionCode).isPresent()) {
                return;
            }
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setTransactionCode(transactionCode);
        transaction.setStatus(status);
        transaction.setGatewayResponse(rawResponse);
        paymentTransactionRepository.save(transaction);
    }
}
