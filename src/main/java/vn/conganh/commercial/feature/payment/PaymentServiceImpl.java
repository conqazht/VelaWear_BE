package vn.conganh.commercial.feature.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.dto.PaymentResponse;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllPayments(Pageable pageable) {
        return ResultPaginationDTO.fromPage(paymentRepository.findAll(pageable)
                .map(PaymentResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        return PaymentResponse.fromEntity(findPayment(id));
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.orderId()));
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(request.provider());
        payment.setTransactionCode(request.transactionCode());
        payment.setAmount(request.amount());
        payment.setStatus(request.status());
        payment.setPaidAt(request.paidAt());
        return PaymentResponse.fromEntity(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse updatePayment(Long id, UpdatePaymentRequest request) {
        Payment payment = findPayment(id);
        if (request.orderId() != null) {
            Order order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.orderId()));
            payment.setOrder(order);
        }
        payment.setProvider(request.provider());
        payment.setTransactionCode(request.transactionCode());
        payment.setAmount(request.amount());
        payment.setStatus(request.status());
        payment.setPaidAt(request.paidAt());
        return PaymentResponse.fromEntity(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public void deletePayment(Long id) {
        paymentRepository.delete(findPayment(id));
    }

    private Payment findPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }
}
