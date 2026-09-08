# transactional-checkout Specification

## Purpose
TBD - created by archiving change add-transactional-checkout-flow. Update Purpose after archive.

## Requirements

### Requirement: Cart checkout creates an order
The system SHALL provide a checkout flow that converts the authenticated user's non-empty cart into a pending order using backend-calculated totals.

#### Scenario: Checkout succeeds from a valid cart
- **WHEN** an authenticated user checks out a cart containing active product variants with sufficient stock
- **THEN** the system creates one order with status `PENDING`, stores backend-calculated `subtotal`, `shippingFee`, `discountAmount`, and `finalAmount`, and does not trust client-submitted monetary totals

#### Scenario: Checkout rejects an empty cart
- **WHEN** an authenticated user checks out with no cart items
- **THEN** the system rejects checkout and creates no order, order items, payment, coupon usage, inventory log, or stock movement

### Requirement: Checkout creates order item snapshots
The system SHALL create order items from cart items and snapshot product and variant information at checkout time.

#### Scenario: Order item snapshot preserves purchase data
- **WHEN** checkout creates an order from a cart item
- **THEN** the corresponding order item stores the variant id, product name, variant name when available, SKU, image when available, unit price, quantity, subtotal, and item status used for that purchase

### Requirement: Checkout atomically reserves stock
The system SHALL reserve product variant stock during checkout using a database atomic conditional update that only succeeds when the variant is active, not deleted, and has enough stock.

#### Scenario: Stock reservation succeeds
- **WHEN** a checkout item requests quantity `q` for an active variant whose stock quantity is at least `q`
- **THEN** the system decrements that variant stock by `q` in the checkout transaction

#### Scenario: Stock reservation fails when stock is insufficient
- **WHEN** a checkout item requests quantity `q` for a variant whose stock quantity is less than `q`
- **THEN** the system rejects checkout and rolls back all checkout writes, including any prior stock decrement or coupon consumption in that transaction

#### Scenario: Concurrent buyers cannot oversell one remaining item
- **WHEN** two checkout transactions concurrently request quantity `1` for the same active variant whose stock quantity is `1`
- **THEN** exactly one checkout succeeds, the other checkout fails with an insufficient stock result, and the final stock quantity is `0`

### Requirement: Checkout applies coupons transactionally
The system SHALL validate and apply a coupon during checkout using backend coupon rules and database-backed usage consumption.

#### Scenario: Valid coupon applies discount
- **WHEN** a checkout request includes an active coupon within its valid date range and the order subtotal satisfies the coupon minimum order amount
- **THEN** the system calculates the discount, applies the coupon maximum discount when configured, stores the order discount amount, and creates a coupon usage linked to the order

#### Scenario: Limited coupon usage is consumed atomically
- **WHEN** a coupon has a usage limit
- **THEN** the system increments `usedCount` with a database atomic conditional update that only succeeds while `usedCount` is below `usageLimit`

#### Scenario: Coupon usage fails when capacity is exhausted
- **WHEN** checkout attempts to use a coupon whose `usedCount` has reached `usageLimit`
- **THEN** the system rejects checkout and rolls back the order, order items, payment, stock reservation, coupon usage, and cart clearing

### Requirement: Checkout is all-or-nothing
The system SHALL execute order creation, coupon usage, stock reservation, order item creation, payment state creation, inventory logging, and cart clearing in a single database transaction.

#### Scenario: Any checkout step fails
- **WHEN** any required checkout step fails after the transaction begins
- **THEN** the system rolls back every database write made by the checkout attempt

### Requirement: Checkout creates pending payment state
The system SHALL create or prepare internal pending payment state for checkout without invoking external payment providers inside the checkout database transaction.

#### Scenario: Online payment checkout creates pending payment
- **WHEN** a checkout uses an online payment method
- **THEN** the system creates an internal payment record with status `PENDING` for the order and leaves provider-specific initiation or callback handling to the payment gateway flow

#### Scenario: COD checkout does not require external provider initiation
- **WHEN** a checkout uses cash on delivery
- **THEN** the system creates the order without requiring an external payment gateway call during checkout

### Requirement: Successful checkout clears checked-out cart items
The system SHALL remove checked-out cart items only after the order, stock reservation, coupon usage, order items, and payment state are successfully created in the transaction.

#### Scenario: Checkout commits successfully
- **WHEN** checkout completes successfully
- **THEN** the system clears the checked-out cart items for that user

#### Scenario: Checkout rolls back
- **WHEN** checkout fails and the transaction rolls back
- **THEN** the user's cart items remain available for correction or retry

### Requirement: Stock and coupon usage are restored on eligible terminal failure
The system SHALL restore reserved stock and release coupon usage when an unpaid pending order is cancelled, payment fails, or payment expires before fulfillment.

#### Scenario: Pending order is cancelled
- **WHEN** an order with reserved stock is cancelled before shipment or delivery
- **THEN** the system increments stock for each order item, releases coupon usage when present, and marks the order as cancelled

#### Scenario: Payment fails before fulfillment
- **WHEN** an online payment fails for an order whose stock was reserved during checkout
- **THEN** the system restores stock, releases coupon usage when present, updates payment state to failed, and moves the order to a failed or cancelled terminal state

#### Scenario: Fulfilled order does not restore stock
- **WHEN** an order has already shipped, been delivered, or otherwise passed the restoration boundary
- **THEN** cancellation or payment handling MUST NOT restore stock or release coupon usage

### Requirement: Restoration is idempotent
The system SHALL ensure stock and coupon restoration for an order is applied at most once.

#### Scenario: Duplicate failure callback is received
- **WHEN** the system receives duplicate payment failure or cancellation handling for the same order
- **THEN** stock and coupon usage are restored only once and the second handling returns the already-terminal result without applying side effects again

### Requirement: Inventory movements are auditable
The system SHALL write inventory log entries for checkout stock reservation and stock restoration events.

#### Scenario: Checkout reserves stock
- **WHEN** checkout decrements stock for an order item
- **THEN** the system writes an inventory log entry identifying the variant, quantity change, movement type, and order or order item reference

#### Scenario: Order restoration returns stock
- **WHEN** cancellation, payment failure, or expiration restores stock for an order item
- **THEN** the system writes an inventory log entry identifying the variant, quantity change, movement type, and order or order item reference
