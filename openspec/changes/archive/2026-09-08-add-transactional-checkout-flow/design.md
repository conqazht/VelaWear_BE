## Context

The backend currently exposes CRUD-style modules for cart, order, payment, coupon, product variant inventory, and inventory logs. `OrderServiceImpl.createOrder` accepts client-provided totals and creates only the `orders` row; it does not derive order items from the cart, reserve stock, consume coupon capacity, or clear the cart. `PaymentServiceImpl.createPayment` creates a payment row, but checkout does not yet define when payment state is created or how payment failure affects reserved inventory.

The existing schema already has useful primitives: `product_variants.stock_quantity`, `coupons.used_count`, `coupons.usage_limit`, `coupon_usages`, `orders.payment_status`, `payments.status`, `order_items`, and `inventory_logs`. This change should connect those primitives into one consistent checkout boundary.

## Goals / Non-Goals

**Goals:**

- Convert a user's cart into an order inside one database transaction.
- Recompute order totals on the backend from product variant prices, coupon rules, shipping fee, and discount rules.
- Prevent overselling by using database atomic conditional updates for stock reservation.
- Prevent coupon overuse by using database atomic conditional updates for coupon usage capacity.
- Snapshot product, variant, price, SKU, image, and quantity into order items.
- Define how pending payment, payment failure, cancellation, and stock/coupon restoration interact.
- Keep the design compatible with the active sandbox payment gateway and notification work.

**Non-Goals:**

- Do not implement real payment gateway provider behavior in this change.
- Do not introduce Redis locks, queues, Serializable transactions, or pessimistic row locks as the default checkout mechanism.
- Do not implement flash sale mechanics yet, but leave the checkout shape extensible for a future campaign/quota resource.
- Do not redesign all order status values beyond the states needed for checkout, payment, cancellation, and restoration.

## Decisions

### Use a dedicated checkout orchestration boundary

Create a checkout-oriented service boundary instead of expanding the current CRUD `createOrder` path into a mixed-purpose endpoint. The checkout service should own the flow from cart to order, while existing admin/internal order CRUD can remain separate.

Rationale: checkout has stronger invariants than manual order creation. It must coordinate cart, stock, coupon, order items, payment state, and rollback.

Alternatives considered:

- Reuse `OrderServiceImpl.createOrder`: rejected because the current request trusts client totals and does not map naturally to cart-derived order creation.
- Put logic inside controller: rejected because transaction and rollback rules belong in a service layer.

### Use atomic conditional DB updates for contended resources

Stock reservation should use a repository update equivalent to:

```sql
UPDATE product_variants
SET stock_quantity = stock_quantity - :quantity
WHERE id = :variantId
  AND stock_quantity >= :quantity
  AND status = 'ACTIVE'
  AND deleted_at IS NULL
```

Coupon usage should use a repository update equivalent to:

```sql
UPDATE coupons
SET used_count = used_count + 1
WHERE id = :couponId
  AND status = 'ACTIVE'
  AND start_date <= :now
  AND end_date >= :now
  AND (usage_limit IS NULL OR used_count < usage_limit)
```

Each update returns an affected row count. `1` means the resource was reserved/consumed; `0` means it is unavailable and checkout must fail.

Rationale: these updates let the database enforce the invariant without long-held application locks. They are enough for the current business rules: checkout, stock decrement, coupon usage, and payment state creation.

Alternatives considered:

- Optimistic locking with `@Version`: useful for admin edits, but less direct for checkout because the business condition is "stock is still enough" rather than "entity version did not change."
- Pessimistic locking with `SELECT ... FOR UPDATE`: safe but heavier and easier to turn into a bottleneck under hot SKUs.
- Redis distributed locks: useful later for flash sale throttling, but not a source of truth for inventory correctness.
- Serializable transaction isolation: too broad for the current needs and more expensive than row-level conditional updates.

### Keep all checkout database writes in one transaction

The checkout transaction should:

1. Load the user's cart and cart items.
2. Load active product variants and product data needed for pricing/snapshots.
3. Recompute subtotal, discount, shipping, and final amount on the backend.
4. Create the pending order row.
5. Atomically consume coupon capacity when a coupon is used.
6. Create a `coupon_usages` row when a coupon is applied.
7. Atomically reserve stock for every cart item.
8. Create order item snapshot rows.
9. Create the pending payment row when the payment method requires a payment record.
10. Clear checked-out cart items.

Any failure after the transaction begins must throw and roll back all writes, including coupon usage and prior stock decrements.

### Do not call external payment providers inside the checkout DB transaction

Checkout should create durable internal order/payment state and commit it before any gateway interaction that may involve network latency. Provider-specific initiation/callback behavior remains in the payment gateway change.

Rationale: holding a database transaction open while calling a provider increases lock duration, failure modes, and user-visible latency.

### Restore stock and coupon capacity only through explicit terminal transitions

When a pending order is cancelled, payment fails, or payment expires before fulfillment, the system should restore stock with an atomic increment and decrement coupon `used_count` when a coupon usage was created. Restoration must be idempotent: retrying a cancel/failure handler must not restore the same order twice.

Fulfilled, shipped, delivered, or already-restored orders must not restore inventory again.

### Record inventory movements

Checkout reservation and restoration should write `inventory_logs` entries so stock movement can be audited. The log should reference the order or order item where possible and describe whether the movement was a checkout reservation or cancellation/payment-failure restoration.

## Risks / Trade-offs

- [Risk] Partial checkout writes could leave stock or coupon counts wrong if not transactional. -> Mitigation: keep stock update, coupon update, order creation, order item creation, coupon usage, payment creation, and cart clearing under one `@Transactional` service method.
- [Risk] A payment callback may be delivered twice. -> Mitigation: make payment finalization and restoration idempotent based on current order/payment status before applying side effects.
- [Risk] Current order status and payment status are plain strings in `Order`. -> Mitigation: centralize allowed transitions in service methods and consider enum constants during implementation.
- [Risk] Concurrent admin stock edits can conflict conceptually with checkout reservations. -> Mitigation: preserve atomic checkout updates and consider a later `@Version` or adjustment-only admin inventory flow.
- [Risk] Future flash sales may add campaign quotas beyond stock and coupon usage. -> Mitigation: model future flash sale quota as another atomic conditional resource inside the same transaction, with Redis only as a front-door throttle if measured load requires it.
- [Risk] Gateway initiation may need an order and payment before returning a payment URL. -> Mitigation: commit checkout first, then let gateway initiation use the committed payment/order record.

## Migration Plan

1. Add repository conditional update methods for stock reservation/restoration and coupon usage/release.
2. Add checkout DTOs and service orchestration without removing existing CRUD endpoints.
3. Add order item creation from cart snapshots and backend total calculation.
4. Add pending payment creation boundary and coordinate provider initiation with the payment gateway change.
5. Add cancellation/payment-failure restoration path.
6. Add focused unit and integration tests, especially concurrent stock and coupon capacity tests.

Rollback strategy: because this change should add new service methods/endpoints and repository methods, rollback can remove or disable the new checkout route while leaving existing CRUD flows intact. Database migrations should be additive where possible.

## Open Questions

- Should checkout expose a new endpoint such as `POST /api/v1/checkout`, or should it be under the order module as `POST /api/v1/orders/checkout`?
- Should coupon usage be one per coupon per user, or only one coupon usage per order as the current `coupon_usages.order_id` uniqueness implies?
- What timeout should mark unpaid pending orders as expired and trigger restoration?
- Should COD orders be considered payment-complete at checkout, or remain payment pending until admin confirmation?
