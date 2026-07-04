# Checkout Module Context

## Transaction Boundary

The `CheckoutService` operates under a single transaction boundary (`@Transactional`) to ensure the all-or-nothing principle for a checkout.

During a checkout:
1. The user's order is created and saved.
2. If a coupon is applied, its validity is checked, and its usage count is incremented atomically (`couponRepository.consumeUsage`).
3. Product stock is reserved (`productVariantRepository.decrementStock`).
4. Order items are saved and inventory logs are recorded.
5. A payment record is initialized for online payment methods.
6. The user's cart is cleared.

If any of these steps fail (e.g., insufficient stock or coupon invalidity), the entire transaction rolls back. Stock is returned, coupon usage is not recorded, and the order is not created.

## Order Cancellation & Payment Failures

Idempotency is guaranteed for cancellation and payment failures. If an order transitions to `CANCELLED`:
- Stock is restored via `productVariantRepository.restoreStock`.
- Coupon usage is released via `couponRepository.releaseUsage`.
- The order status and payment status are updated.

This ensures that resources (stock, coupons) are never orphaned if an order does not proceed to completion.

## Payment State

For online payment methods (e.g., VNPay, MoMo), the checkout flow creates a `Payment` entity with a status of `PENDING`. However, it does not directly interact with payment provider APIs (e.g., redirects, webhooks, signatures). 

The actual integration with third-party payment gateways is handled by the dedicated `Payment Gateway` module. Clients receive the `paymentId` from the checkout response and interact with the Payment module to initiate the transaction and handle callbacks.
