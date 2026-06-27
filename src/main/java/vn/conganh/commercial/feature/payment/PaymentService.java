package vn.conganh.commercial.feature.payment;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.payment.dto.PaymentResponse;
import vn.conganh.commercial.feature.payment.dto.PaymentFilterRequest;

public interface PaymentService {

    ResultPaginationDTO getAllPayments(PaymentFilterRequest filter, Pageable pageable);

    PaymentResponse getPaymentById(Long id);

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse updatePayment(Long id, UpdatePaymentRequest request);

    void deletePayment(Long id);
}
