## 1. Payment Schema and Configuration

- [ ] 1.1 Add `ZALOPAY` and `SEPAY` to the payment provider enum
- [ ] 1.2 Add a Flyway migration that allows `ZALOPAY` and `SEPAY` in the `payments.provider` check constraint
- [ ] 1.3 Add sandbox payment configuration properties for global payment environment, provider enabled flags, sandbox endpoints, credentials, return URLs, callback URLs, and simulation enablement
- [ ] 1.4 Update `.env.example` with sandbox payment variables and safe placeholder values
- [ ] 1.5 Add configuration tests or startup validation for required sandbox provider settings when a provider is enabled
- [ ] 1.6 Add guardrails so live-looking provider URLs or production-mode settings are rejected for this sandbox-only phase

## 2. Payment Gateway Initiation

- [ ] 2.1 Add payment initiation request/response DTOs with provider, order id, status, redirect URL, QR/bank-transfer data, and payment id
- [ ] 2.2 Add a `PaymentGateway` abstraction and gateway router keyed by provider
- [ ] 2.3 Add a shared payment orchestration service that validates order amount/status and creates or reuses a pending payment before routing to provider adapters
- [ ] 2.4 Implement COD initiation without external gateway calls
- [ ] 2.5 Implement MoMo sandbox initiation using configured sandbox endpoint and credentials
- [ ] 2.6 Implement VNPay sandbox payment URL creation with signed parameters
- [ ] 2.7 Implement ZaloPay sandbox initiation using configured app id, key material, endpoint, callback URL, and redirect URL
- [ ] 2.8 Implement SePay-style bank-transfer/QR initiation using provider `SEPAY` and order method `BANK_TRANSFER`
- [ ] 2.9 Add optional Stripe test-mode initiation guarded by provider enablement
- [ ] 2.10 Add backend payment initiation endpoint that delegates common work to the shared orchestration service instead of branching per provider

## 3. Callback and State Handling

- [ ] 3.1 Add callback/webhook endpoints for MoMo, VNPay, ZaloPay, SePay-style, and optional Stripe test-mode provider payloads
- [ ] 3.2 Implement provider-specific signature, MAC, or checksum verification inside adapters where applicable
- [ ] 3.3 Normalize provider callback results into a common payment result model
- [ ] 3.4 Add a shared callback processing service that stores accepted callbacks as `PaymentTransaction` records with transaction code, status, and raw gateway response
- [ ] 3.5 Update `Payment` and `Order.paymentStatus` transactionally from successful and failed callback results through the shared callback path
- [ ] 3.6 Ensure browser return URL handling only redirects/polls status and does not mark payments successful from frontend-controlled data alone

## 4. Idempotency and Simulation

- [ ] 4.1 Add idempotency checks for duplicate provider transaction codes and repeated terminal statuses
- [ ] 4.2 Prevent conflicting terminal callbacks from silently overwriting finalized payment state
- [ ] 4.3 Add dev/test-only payment success simulation endpoint guarded by profile or explicit configuration
- [ ] 4.4 Add dev/test-only payment failure simulation endpoint guarded by profile or explicit configuration
- [ ] 4.5 Route simulation results through the same shared callback/state-transition path used by verified provider callbacks
- [ ] 4.6 Add tests proving simulation endpoints are disabled when simulation is not enabled

## 5. Commerce Email Notifications

- [ ] 5.1 Add order/payment notification event models for order created, payment succeeded, payment failed, and order completed
- [ ] 5.2 Publish order-created notification after order creation commits
- [ ] 5.3 Publish payment-succeeded and payment-failed notifications only on real payment state transitions
- [ ] 5.4 Publish order-completed notification only when order status transitions to `COMPLETED`
- [ ] 5.5 Add email handlers/templates for order confirmation, payment success, payment failure, and completed/received order emails using the phase 1 email provider abstraction
- [ ] 5.6 Ensure notification failures are logged or recorded without rolling back committed order/payment state

## 6. Documentation and Tests

- [ ] 6.1 Update API documentation for sandbox-only payment initiation, callbacks, return/status behavior, provider enablement, and simulation endpoints
- [ ] 6.2 Update database documentation for providers `ZALOPAY` and `SEPAY`, plus bank-transfer provider/method mapping
- [ ] 6.3 Add service tests for shared orchestration and each gateway adapter with external calls mocked
- [ ] 6.4 Add callback tests for success, failure, invalid signature, duplicate callback, and conflicting terminal callback
- [ ] 6.5 Add integration tests for order/payment state transitions and notification dispatch
- [ ] 6.6 Add Bruno or API examples for sandbox initiation, callback simulation, provider disabled cases, and order/payment status checks
- [ ] 6.7 Run focused payment/notification tests and the full Maven test suite
