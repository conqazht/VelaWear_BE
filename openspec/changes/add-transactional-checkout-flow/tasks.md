## 1. API and Domain Boundaries

- [ ] 1.1 Define checkout request/response DTOs for cart checkout, receiver information, payment method, shipping fee input, and optional coupon code.
- [ ] 1.2 Add a checkout service boundary separate from generic order CRUD, with a transactional method that returns the created order/payment summary.
- [ ] 1.3 Define domain exceptions or error responses for empty cart, inactive variant, insufficient stock, invalid coupon, exhausted coupon usage, and invalid order transition.
- [ ] 1.4 Decide and add the checkout endpoint route, keeping it separate from provider-specific payment initiation.

## 2. Atomic Resource Operations

- [ ] 2.1 Add a `ProductVariantRepository` conditional stock decrement method that updates stock only when the variant is active, not deleted, and has sufficient stock.
- [ ] 2.2 Add a stock restoration method that increments stock for order cancellation or payment failure restoration.
- [ ] 2.3 Add a `CouponRepository` conditional usage consumption method that increments `usedCount` only when the coupon is active, valid by time, and below `usageLimit`.
- [ ] 2.4 Add a coupon usage release method for eligible order cancellation, payment failure, or payment expiration.
- [ ] 2.5 Add repository/query helpers needed to find cart items, variants, coupon usage, and order items for checkout and restoration.

## 3. Checkout Transaction

- [ ] 3.1 Load the authenticated user's cart items and reject checkout when the cart is empty.
- [ ] 3.2 Load active product variants and product data required for backend pricing and order item snapshots.
- [ ] 3.3 Recalculate subtotal, discount amount, shipping fee, and final amount on the backend.
- [ ] 3.4 Create the pending order row with backend-calculated totals and receiver/payment metadata.
- [ ] 3.5 Apply coupon validation and atomic coupon usage consumption when a coupon is supplied.
- [ ] 3.6 Create a `CouponUsage` row linked to the order when a coupon is applied.
- [ ] 3.7 Reserve stock for every cart item using atomic stock decrement and fail the transaction if any item cannot be reserved.
- [ ] 3.8 Create order item snapshot rows from the checked-out cart items.
- [ ] 3.9 Create pending internal payment state for online payment methods without calling external payment providers inside the checkout transaction.
- [ ] 3.10 Clear checked-out cart items only after all prior checkout writes succeed.

## 4. Restoration and State Transitions

- [ ] 4.1 Implement an order cancellation path that restores reserved stock and releases coupon usage only for eligible unpaid pending orders.
- [ ] 4.2 Implement a payment failure or expiration handling path that restores stock, releases coupon usage, and moves the order/payment to a terminal failed or cancelled state.
- [ ] 4.3 Make restoration idempotent so duplicate cancellation or payment failure handling does not restore stock or coupon usage more than once.
- [ ] 4.4 Write inventory log entries for checkout reservation and restoration events.
- [ ] 4.5 Centralize order/payment transition rules so shipped, delivered, or already-restored orders cannot restore resources again.

## 5. Tests

- [ ] 5.1 Add service tests for successful checkout from cart, backend total calculation, order item snapshots, payment pending state, and cart clearing.
- [ ] 5.2 Add rollback tests proving failed stock reservation restores any prior stock/coupon changes and leaves cart items intact.
- [ ] 5.3 Add rollback tests proving failed coupon consumption leaves stock, order, order items, payment, coupon usage, and cart unchanged.
- [ ] 5.4 Add a concurrent checkout integration test where two buyers attempt to purchase the last unit and exactly one checkout succeeds.
- [ ] 5.5 Add a concurrent coupon usage test where two buyers attempt to use the final limited coupon usage and exactly one checkout succeeds.
- [ ] 5.6 Add cancellation/payment failure tests proving stock and coupon usage restore once and duplicate handling is idempotent.
- [ ] 5.7 Add controller/API tests for checkout success and expected validation failures.

## 6. Documentation and Coordination

- [ ] 6.1 Document the checkout endpoint request/response and error cases in the API documentation.
- [ ] 6.2 Document the internal boundary between checkout-created pending payment state and the sandbox payment gateway initiation/callback flow.
- [ ] 6.3 Run the backend test suite or targeted module tests and capture any follow-up tasks before applying the change.
