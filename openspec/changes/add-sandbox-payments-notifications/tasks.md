## 1. Payment Schema and Configuration

- [ ] 1.1 Add `SEPAY` to the payment provider enum
- [ ] 1.2 Add a Flyway migration that allows `SEPAY` in the `payments.provider` check constraint
- [ ] 1.3 Add sandbox payment configuration properties for provider enabled flags, endpoints, credentials, return URLs, callback URLs, and simulation enablement
- [ ] 1.4 Update `.env.example` with sandbox payment variables and safe placeholder values
- [ ] 1.5 Add configuration tests or startup validation for required provider settings when a provider is enabled

## 2. Payment Gateway Initiation

- [ ] 2.1 Add payment initiation request/response DTOs with provider, order id, status, redirect URL, QR/bank-transfer data, and payment id
- [ ] 2.2 Add a `PaymentGateway` abstraction and gateway router keyed by provider
- [ ] 2.3 Implement COD initiation without external gateway calls
- [ ] 2.4 Implement MoMo sandbox initiation using configured sandbox endpoint and credentials
- [ ] 2.5 Implement VNPay sandbox payment URL creation with signed parameters
- [ ] 2.6 Implement SePay-style bank-transfer/QR initiation using provider `SEPAY` and order method `BANK_TRANSFER`
- [ ] 2.7 Add backend payment initiation endpoint that validates order amount/status and creates or reuses a pending payment

## 3. Callback and State Handling

- [ ] 3.1 Add callback/webhook endpoints for MoMo, VNPay, and SePay-style provider payloads
- [ ] 3.2 Implement provider-specific signature or checksum verification where applicable
- [ ] 3.3 Normalize provider callback results into a common payment result model
- [ ] 3.4 Store each accepted callback as a `PaymentTransaction` with transaction code, status, and raw gateway response
- [ ] 3.5 Update `Payment` and `Order.paymentStatus` transactionally from successful and failed callback results
- [ ] 3.6 Ensure browser return URL handling does not mark payments successful from frontend-controlled data alone

## 4. Idempotency and Simulation

- [ ] 4.1 Add idempotency checks for duplicate provider transaction codes and repeated terminal statuses
- [ ] 4.2 Prevent conflicting terminal callbacks from silently overwriting finalized payment state
- [ ] 4.3 Add dev/test-only payment success simulation endpoint guarded by profile or explicit configuration
- [ ] 4.4 Add dev/test-only payment failure simulation endpoint guarded by profile or explicit configuration
- [ ] 4.5 Add tests proving simulation endpoints are disabled when simulation is not enabled

## 5. Commerce Email Notifications

- [ ] 5.1 Add order/payment notification event models for order created, payment succeeded, payment failed, and order completed
- [ ] 5.2 Publish order-created notification after order creation commits
- [ ] 5.3 Publish payment-succeeded and payment-failed notifications only on real payment state transitions
- [ ] 5.4 Publish order-completed notification only when order status transitions to `COMPLETED`
- [ ] 5.5 Add email handlers/templates for order confirmation, payment success, payment failure, and completed/received order emails using the phase 1 email provider abstraction
- [ ] 5.6 Ensure notification failures are logged or recorded without rolling back committed order/payment state

## 6. Documentation and Tests

- [ ] 6.1 Update API documentation for payment initiation, callbacks, return/status behavior, and simulation endpoints
- [ ] 6.2 Update database documentation for provider `SEPAY` and bank-transfer provider/method mapping
- [ ] 6.3 Add service tests for each gateway adapter with external calls mocked
- [ ] 6.4 Add callback tests for success, failure, invalid signature, duplicate callback, and conflicting terminal callback
- [ ] 6.5 Add integration tests for order/payment state transitions and notification dispatch
- [ ] 6.6 Add Bruno or API examples for sandbox initiation, callback simulation, and order/payment status checks
- [ ] 6.7 Run focused payment/notification tests and the full Maven test suite
