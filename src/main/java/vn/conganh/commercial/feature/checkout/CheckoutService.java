package vn.conganh.commercial.feature.checkout;

import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewResponse;

public interface CheckoutService {

    CheckoutResponse checkout(CheckoutRequest request, String userEmail);

    CheckoutResponse checkout(CheckoutRequest request, String userEmail, String idempotencyKey);

    CheckoutPreviewResponse preview(CheckoutPreviewRequest request, String userEmail);

    void cancelOrder(long orderId, String userEmail);

    void handlePaymentFailure(long orderId);
}
