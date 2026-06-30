## Context

The backend already has `orders`, `payments`, and `payment_transactions` tables plus CRUD-style order/payment services. `PaymentProvider` currently includes `COD`, `VNPAY`, `MOMO`, and `STRIPE`, while the order schema supports `COD`, `VNPAY`, `MOMO`, and `BANK_TRANSFER` as payment methods. Checkout is not yet gateway-driven: the system can store a payment, but it cannot initiate sandbox payments, receive provider callbacks, or automatically notify customers when payment/order state changes.

This phase follows the OTP/email foundation from `add-auth-otp-email`. It keeps all payment logic in the backend and lets the frontend only select a provider, redirect/render QR, and read order/payment status.

## Goals / Non-Goals

**Goals:**
- Add sandbox-first payment initiation for COD, MoMo, VNPay, and SePay-style bank transfer.
- Keep sandbox versus production differences in configuration, credentials, and endpoint URLs.
- Persist every provider callback/simulation as a `PaymentTransaction` with raw gateway response data.
- Update `Payment` and `Order` statuses idempotently from webhook/IPN/simulation results.
- Add dev/test-only simulation endpoints to support demos without real credentials or money movement.
- Send order-created, payment-succeeded, payment-failed, and order-completed/received emails through the email provider abstraction from phase 1.

**Non-Goals:**
- Do not integrate production/live merchant credentials.
- Do not process real money in this phase.
- Do not implement refunds, disputes, partial captures, or settlement reconciliation.
- Do not make frontend responsible for calling MoMo, VNPay, SePay, or email provider APIs directly.
- Do not treat browser return URLs as the source of truth for payment success.

## Decisions

### 1. Payment orchestration stays in backend
- **Decision:** Add a backend payment initiation flow that accepts an order and provider, creates or reuses a pending `Payment`, calls the configured gateway adapter, and returns a redirect URL, QR payload, or immediate COD result.
- **Rationale:** Provider secrets, signatures, amount validation, and order/payment state transitions belong in backend services.
- **Alternatives considered:**
  - Frontend calls provider directly: leaks provider details and makes callback/state verification fragile.
  - Continue payment CRUD only: simple, but does not model real checkout behavior.

### 2. Gateway router with provider adapters
- **Decision:** Introduce a `PaymentGateway` abstraction and route by `PaymentProvider`.
- **Adapters:**
  - `CodPaymentGateway` marks COD payments as pending/unpaid or awaiting collection without external calls.
  - `MomoSandboxPaymentGateway` creates sandbox payment sessions and validates sandbox IPN/callback payloads.
  - `VnpaySandboxPaymentGateway` creates sandbox payment URLs and validates return/IPN payload checksums.
  - `SepaySandboxPaymentGateway` models bank-transfer QR/webhook behavior and maps SePay technical provider to `BANK_TRANSFER` order method.
- **Rationale:** Adapters isolate provider-specific signing and payload shape from domain state transitions.

### 3. SePay as provider, bank transfer as method
- **Decision:** Treat `SEPAY` as a `PaymentProvider` while using `BANK_TRANSFER` as the order-facing payment method.
- **Rationale:** Customers choose bank transfer/QR as the method, while SePay is the technical webhook provider. This keeps business language and integration details separate.
- **Required migration:** Add `SEPAY` to the payment provider enum and database check constraint.

### 4. Webhook/IPN/simulation is the source of truth
- **Decision:** Provider callback/webhook/IPN handlers update payment/order state after verifying provider-specific authenticity where applicable. Browser return URLs only help the frontend display a status page and trigger status polling.
- **Rationale:** Users can close the browser, forge query params, or return before asynchronous provider confirmation arrives.
- **Idempotency:** Repeated callbacks for the same provider transaction code and status MUST not double-send emails or corrupt order/payment state.

### 5. Dev-only simulation endpoints
- **Decision:** Add simulation endpoints for success/failure callback behavior, guarded by `dev`/`test` profile or an explicit simulation-enabled property.
- **Rationale:** The project is personal and sandbox-first; simulation keeps demos and tests reliable when external sandbox credentials are missing.
- **Constraint:** Simulation endpoints MUST be disabled in production-like profiles.

### 6. Domain events for email notifications
- **Decision:** Publish internal events after successful transactions/status changes, then handle emails separately from core order/payment mutation.
- **Events:**
  - `OrderCreatedEvent`
  - `PaymentSucceededEvent`
  - `PaymentFailedEvent`
  - `OrderCompletedEvent`
- **Rationale:** Payment/order services should not be tightly coupled to templates or provider-specific email calls.
- **Delivery:** Reuse the phase 1 email provider abstraction. Notification failures should be logged and surfaced for observability, but they must not roll back already-committed payment success.

### 7. Transaction boundaries
- **Decision:** Update `Payment`, `PaymentTransaction`, and `Order` inside a single transaction for each callback/simulation result, then publish notification work after commit where practical.
- **Rationale:** Payment state must remain internally consistent even if email sending fails later.

## Risks / Trade-offs

- **[Risk] Sandbox providers differ from production in small details.** Mitigation: keep provider endpoints and credentials configurable and isolate provider signing in adapters.
- **[Risk] Duplicate callbacks send duplicate emails.** Mitigation: enforce idempotency by provider transaction code/status and send notifications only on state transition.
- **[Risk] Simulation endpoints accidentally exposed.** Mitigation: guard with profile/property and tests that production config disables them.
- **[Risk] Payment success email fails after payment succeeds.** Mitigation: log notification failure, keep payment/order state committed, and allow future retry mechanism.
- **[Risk] SePay provider/method naming confusion.** Mitigation: document the distinction: `order.paymentMethod=BANK_TRANSFER`, `payment.provider=SEPAY`.
- **[Risk] Existing CRUD payment endpoints allow manual inconsistent updates.** Mitigation: gateway-driven payment APIs should become the preferred path for checkout while admin CRUD remains controlled by RBAC.

## Migration Plan

1. Add `SEPAY` to provider enum and Flyway check constraint migration.
2. Add payment gateway configuration properties for sandbox provider endpoints, return URLs, callback URLs, credentials, and simulation enablement.
3. Add gateway request/response DTOs and gateway router/adapters.
4. Add payment initiation endpoint and provider callback/webhook endpoints.
5. Add idempotent callback handling that writes `PaymentTransaction`, updates `Payment`, and updates `Order.paymentStatus`.
6. Add dev/test simulation endpoints or fixtures for success/failure outcomes.
7. Add order/payment/completed notification events and email handlers using the phase 1 email provider abstraction.
8. Add tests for initiation, callback verification, idempotency, simulation guardrails, state transitions, and email event dispatch.

Rollback keeps existing CRUD endpoints and tables intact. New provider configuration can be disabled, and simulation endpoints remain profile/property guarded.

## Open Questions

- Should the first implementation include all three sandbox adapters, or implement one real sandbox adapter plus mocks for the others?
- Should payment initiation create the order and payment together, or require the order to exist first as `POST /payments/initiate` currently suggests?
- Should notification events be handled synchronously after commit in phase 2, or queued for retry in a later change?
