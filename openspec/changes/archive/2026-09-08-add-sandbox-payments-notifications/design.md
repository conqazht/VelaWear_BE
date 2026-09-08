## Context

The backend already has `orders`, `payments`, and `payment_transactions` tables plus order/payment services. `PaymentProvider` includes `COD`, `SEPAY`, `VNPAY`, `MOMO`, and `STRIPE`, while the order schema supports `COD`, `SEPAY`, `VNPAY`, `MOMO`, and `BANK_TRANSFER`. Currently, `COD` and `SEPAY` (VietQR) are implemented. This change adds complete sandbox/test integration for **VNPay**, **MoMo**, and **Stripe**, backed by a resilient, extensible architecture using the **Adapter Pattern**, **Stripe Java SDK**, cryptographic signature verification, strict **IPN Idempotency**, **Dev Simulation endpoints**, and **Domain Event-driven Transactional Notifications**.

This phase follows the OTP/email foundation from `add-auth-otp-email`. It keeps all payment secrets, signing, and state transitions strictly on the backend, while the frontend selects a provider, redirects to checkout / displays QR, and queries order/payment status.

## Goals / Non-Goals

**Goals:**
- Add payment initiation and webhook verification for **COD**, **MoMo** (captureWallet AIO), **VNPay** (HMAC-SHA512), **SePay** (VietQR), and **Stripe** (Stripe Checkout Session via official SDK).
- Eliminate ZaloPay to focus on the key Vietnamese wallets and global card standard.
- Structure gateways using the **Adapter Pattern** (`PaymentGateway` interface + router), ensuring zero changes to `CheckoutService` when adding or updating payment providers.
- Maintain provider credentials, sandbox endpoints, return URLs, and callback URLs securely in `application.yml` and environment variables.
- Handle Webhook/IPN callbacks idempotently: verify signatures, deduplicate by provider transaction code and Stripe event ID, persist raw gateway payloads in `payment_transactions.gateway_response`, and update `Payment` and `Order` status transactionally.
- Provide dev-only simulation endpoints for instant success/failure callbacks to support reliable automated tests and local demos without external dependencies.
- Align payment expiration windows (default 15 minutes) with the reservation TTL and scheduler.
- Dispatch domain notification events (`OrderCreatedEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `OrderCompletedEvent`) handled asynchronously via the email outbox to guarantee core payment state is never rolled back by email delivery errors.
- Provide comprehensive developer sandbox guides (`docs/VNPAY_SANDBOX_GUIDE.md`, `docs/MOMO_SANDBOX_GUIDE.md`, `docs/STRIPE_TEST_GUIDE.md`) with test card numbers and testing procedures.

**Non-Goals:**
- Do not hardcode live production credentials in source control (sandbox and production share identical logic, differentiated only by environment variables).
- Do not process real money in this phase.
- Do not implement refunds, dispute webhooks, partial captures, or multi-currency conversion in this phase.
- Do not allow frontend code to call MoMo, VNPay, Stripe, or email provider APIs directly.
- Do not treat browser return URLs as the authoritative source of payment success (IPN/webhook is the authoritative source).

## Decisions

### 1. Payment orchestration stays in backend
- **Decision:** Backend payment initiation validates order eligibility and amount, creates or reuses a pending `Payment`, calls the gateway adapter, and returns a unified response containing `actionUrl`, `qrCode`, or immediate COD confirmation.
- **Rationale:** Keeps secret keys, signature generation, and state transitions secure and isolated on the backend.

### 2. Gateway router with provider adapters (Adapter Pattern)
- **Decision:** Introduce a `PaymentGateway` interface and a `PaymentGatewayRouter` (or Factory) keyed by `PaymentProvider`.
- **Adapters:**
  - `CodPaymentGateway`: Handles Cash-On-Delivery without external calls.
  - `SepaySandboxPaymentGateway`: Handles VietQR bank transfer reference data and IPN verification.
  - `VnpaySandboxPaymentGateway`: Generates signed VNPay URL with ASCII sorted parameters and HMAC-SHA512 hashing; validates IPN checksums and returns standard VNPay IPN response payload (`{"RspCode":"00","Message":"Confirm Success"}`).
  - `MomoSandboxPaymentGateway`: Sends HTTP request to MoMo All-In-One API (`/v2/gateway/api/create`) with HMAC-SHA256 signature; verifies MoMo IPN signature and returns MoMo acknowledge response.
  - `StripeTestPaymentGateway`: Uses official `com.stripe:stripe-java` SDK to create `SessionCreateParams` (Card payment, line items, success/cancel URLs, idempotency key); verifies webhook signatures using `Webhook.constructEvent(payload, sigHeader, secret)`.
- **Rationale:** Adapters isolate provider-specific API formats, signing algorithms, and response codes. The core checkout and callback state machine remains completely clean.

### 3. Webhook / IPN is the authoritative source of truth
- **Decision:** Order payment status is only transitioned to `PAID` via verified IPN/Webhook or authorized Dev Simulation. Browser return URLs only redirect the user and trigger frontend order status polling.
- **Rationale:** Users can close the browser before returning, or manipulate return URL query parameters.

### 4. Strict Idempotency and Audit
- **Decision:** 
  - Each incoming IPN/Webhook is verified against the provider transaction code / Stripe event ID.
  - If a transaction has already been processed with the same terminal status, return an idempotent success response immediately without re-processing.
  - Every callback payload is audited into `payment_transactions.gateway_response` as JSON.

### 5. Dev-Only Simulation Endpoints
- **Decision:** Provide endpoints (`POST /api/v1/payments/simulate/success`, `POST /api/v1/payments/simulate/failed`) guarded by `payment.simulation.enabled=true` (or `dev`/`test` profiles).
- **Rationale:** Enables deterministic CI/CD and offline demonstrations without requiring active third-party sandbox accounts or tunnels (e.g. ngrok).

### 6. Alignment of Payment Expiration and Reservation TTL
- **Decision:** Payment initiation sets the provider expiry time (e.g. `vnp_ExpireDate` for VNPay, `expiresAt` for Stripe) capped by the order reservation expiration time (default 15 minutes).
- **Rationale:** Avoids race conditions where a customer pays after the inventory reservation has been released and reclaimed by another customer.

### 7. Domain Events for Asynchronous Commerce Notifications
- **Decision:** Publish Spring Application Events (`OrderCreatedEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `OrderCompletedEvent`) after transactions commit.
- **Rationale:** Payment processing remains blazing fast and decoupled from email rendering or network delivery. If email sending encounters a temporary failure, payment commit is preserved.

## Risks / Trade-offs

- **[Risk] Difference between Sandbox and Production:** Logic and algorithms are 100% identical; only endpoints and API keys differ. Mitigated by keeping endpoints and keys strictly configurable in `.env` / `application.yml`.
- **[Risk] Webhook delivery failures in local dev:** Local developer machines are not publicly accessible by VNPay/MoMo/Stripe without ngrok/localtunnel. Mitigated by providing dev simulation endpoints and documenting ngrok setup in guides.
- **[Risk] Duplicate Webhook callbacks:** Providers may retry webhooks up to multiple times. Mitigated by checking transaction code / event ID existence and returning idempotent 200 OK.
- **[Risk] Secret leakage:** Mitigated by `.env.example` placeholders, gitignore enforcement, and backend-only gateway execution.

## Migration Plan

1. **Schema:** Existing database check constraints already allow `COD`, `SEPAY`, `VNPAY`, `MOMO`, and `STRIPE` (no DB migration needed).
2. **Configuration:** Add configuration properties in `application.yml` and `.env.example` for VNPay (`tmn-code`, `hash-secret`, `pay-url`, `return-url`), MoMo (`partner-code`, `access-key`, `secret-key`, `endpoint`, `return-url`, `ipn-url`), Stripe (`secret-key`, `webhook-secret`, `success-url`, `cancel-url`), and simulation toggle.
3. **Gateway Layer:** Add `PaymentGateway` interface, `PaymentGatewayRouter`, and adapter classes for `VNPAY`, `MOMO`, `STRIPE`, `SEPAY`, and `COD`.
4. **Controllers:** Add `/api/v1/payments/initiate`, `/api/v1/payments/{provider}/callback`, `/api/v1/payments/{provider}/ipn`, and `/api/v1/payments/simulate/*`.
5. **Notifications:** Implement event models and email template renderers for order/payment lifecycle events.
6. **Documentation & Tests:** Add unit tests, integration tests, and sandbox guides (`VNPAY_SANDBOX_GUIDE.md`, `MOMO_SANDBOX_GUIDE.md`, `STRIPE_TEST_GUIDE.md`).

## Open Questions
- None. Requirements and scope for COD, SePay, VNPay, MoMo, and Stripe are finalized and aligned with codebase architecture.
