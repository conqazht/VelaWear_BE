package vn.conganh.commercial.feature.payment;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.payment.dto.PaymentResponse;

public interface PaymentService {

    ResultPaginationDTO getAllPayments(Pageable pageable);

    PaymentResponse getPaymentById(Long id);

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse updatePayment(Long id, UpdatePaymentRequest request);

    void deletePayment(Long id);
}
