package vn.conganh.commercial.feature.payment;

import java.util.List;
import vn.conganh.commercial.feature.payment.dto.PaymentResponse;

public interface PaymentService {

    List<PaymentResponse> getAllPayments();

    PaymentResponse getPaymentById(Long id);

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse updatePayment(Long id, UpdatePaymentRequest request);

    void deletePayment(Long id);
}
