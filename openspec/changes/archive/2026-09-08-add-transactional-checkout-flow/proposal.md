## Why

The backend has CRUD entities for carts, orders, payments, coupons, product variants, and inventory logs, but it does not yet have a single checkout transaction that turns a cart into an order while safely consuming stock and coupon capacity. This leaves the project without a database-enforced answer for overselling, coupon overuse, order-item creation, and rollback when checkout partially fails.

## What Changes

- Add a transactional checkout flow that creates an order from the authenticated user's cart instead of trusting client-submitted totals.
- Reserve product stock during checkout with database atomic conditional updates so concurrent buyers cannot oversell the same variant.
- Apply coupons during checkout with database-backed validation and atomic usage consumption when a coupon has limited usage.
- Create order items as immutable snapshots of variant/product data at checkout time.
- Create or prepare the payment record/state needed by the existing payment gateway work without replacing gateway-specific initiation/callback behavior.
- Restore reserved stock and release coupon usage when an eligible pending order is cancelled or payment fails/expires.
- Record inventory movement logs for checkout reservation and restoration events.

## Capabilities

### New Capabilities

- `transactional-checkout`: Covers cart-to-order checkout, atomic stock reservation, coupon redemption, order item snapshotting, payment-state boundaries, and rollback/restore behavior.

### Modified Capabilities

- None.

## Impact

- Affected backend areas: cart, order, order item, product variant inventory, coupon, coupon usage, payment state, and inventory log modules.
- Adds repository-level conditional update operations for stock and coupon usage.
- Adds service-level checkout orchestration under a single database transaction.
- Adds tests for concurrent checkout when stock or coupon usage is limited.
- Coordinates with the active `add-sandbox-payments-notifications` change by defining the checkout/payment state boundary while leaving provider-specific payment initiation, callbacks, and notifications to that change.
