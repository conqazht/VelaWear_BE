package vn.conganh.commercial.feature.checkout;

import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;

public interface CheckoutService {

    CheckoutResponse checkout(CheckoutRequest request, String userEmail);

    void cancelOrder(long orderId, String userEmail);

    void handlePaymentFailure(long orderId);
}
