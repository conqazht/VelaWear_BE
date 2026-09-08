## 1. Dependencies and Configuration
 
- [ ] 1.1 Verify payment provider enums (`COD`, `SEPAY`, `VNPAY`, `MOMO`, `STRIPE`) and database constraints (no migration needed as schema already supports all 5 providers)
- [ ] 1.2 Add official Stripe Java SDK dependency (`com.stripe:stripe-java`) in `pom.xml`
- [ ] 1.3 Add payment configuration properties for global sandbox environment, provider enabled flags, sandbox endpoints, credentials, return URLs, callback/IPN URLs, and simulation enablement
- [ ] 1.4 Update `.env.example` with sandbox payment variables and safe placeholder values (VNPay, MoMo, Stripe, SePay)
- [ ] 1.5 Add configuration validation and startup checks ensuring production keys are prevented in sandbox mode
 
## 2. Payment Gateway Initiation
 
- [ ] 2.1 Add payment initiation request/response DTOs with provider, order id, amount, redirect URL, QR data, and payment id
- [ ] 2.2 Add `PaymentGateway` abstraction and `PaymentGatewayRouter` keyed by `PaymentProvider` (Adapter Pattern)
- [ ] 2.3 Add shared payment orchestration service that validates order amount/status and creates or reuses a pending payment
- [ ] 2.4 Implement `CodPaymentGateway` without external calls
- [ ] 2.5 Implement `MomoSandboxPaymentGateway` using HMAC-SHA256 signature and MoMo All-In-One API (`/v2/gateway/api/create`)
- [ ] 2.6 Implement `VnpaySandboxPaymentGateway` with ASCII sorted parameters, URL encoding, and HMAC-SHA512 signature
- [ ] 2.7 Implement `StripeTestPaymentGateway` using Stripe SDK (`Session.create` with Mode.PAYMENT, Card, line items, and idempotency key)
- [ ] 2.8 Adapt `SepaySandboxPaymentGateway` for VietQR reference generation
- [ ] 2.9 Align payment URL/session expiration with checkout reservation TTL (capping expiry at 15 minutes)
- [ ] 2.10 Add backend payment initiation endpoint (`POST /api/v1/payments/initiate`) delegating cleanly to the gateway router
 
## 3. Callback, Webhooks, and State Handling
 
- [ ] 3.1 Add callback/webhook endpoints for VNPay (`/api/v1/payments/vnpay/callback` & `/ipn`), MoMo (`/api/v1/payments/momo/ipn`), Stripe (`/api/v1/payments/stripe/webhook`), and SePay
- [ ] 3.2 Implement provider-specific signature verification:
  - VNPay: verify `vnp_SecureHash` and return standard JSON `{"RspCode":"00","Message":"Confirm Success"}`
  - MoMo: verify HMAC-SHA256 signature and return MoMo acknowledge response
  - Stripe: verify `Stripe-Signature` header via `Webhook.constructEvent(payload, sigHeader, secret)`
  - SePay: verify secret token / signature
- [ ] 3.3 Normalize provider callback results into unified `PaymentResult`
- [ ] 3.4 Add shared callback processing service storing `PaymentTransaction` records with transaction code, status, and raw gateway response JSON
- [ ] 3.5 Update `Payment` and `Order.paymentStatus` (`PAID` or `FAILED`) transactionally through the shared callback path
- [ ] 3.6 Ensure browser return URLs only redirect to frontend status polling and never mark orders as paid directly
 
## 4. Idempotency and Dev Simulation
 
- [ ] 4.1 Add idempotency checks for duplicate provider transaction codes and Stripe event IDs to prevent repeated processing
- [ ] 4.2 Prevent conflicting terminal callbacks from overwriting already finalized payment state
- [ ] 4.3 Add dev/test-only payment success simulation endpoint (`POST /api/v1/payments/simulate/success`) guarded by `payment.simulation.enabled`
- [ ] 4.4 Add dev/test-only payment failure simulation endpoint (`POST /api/v1/payments/simulate/failed`) guarded by `payment.simulation.enabled`
- [ ] 4.5 Route simulation results through the exact same state-transition path as verified provider callbacks
- [ ] 4.6 Add tests verifying simulation endpoints are disabled outside dev/test environments
 
## 5. Commerce Email Notifications
 
- [ ] 5.1 Add order/payment notification domain events (`OrderCreatedEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `OrderCompletedEvent`)
- [ ] 5.2 Publish `OrderCreatedEvent` after order creation commits
- [ ] 5.3 Publish `PaymentSucceededEvent` and `PaymentFailedEvent` only on actual payment state transitions
- [ ] 5.4 Publish `OrderCompletedEvent` when order status transitions to `COMPLETED`
- [ ] 5.5 Add email listeners/templates for order confirmation, payment success, payment failure, and order delivered/completed
- [ ] 5.6 Ensure notification failures are logged without rolling back committed payment/order state
 
## 6. Documentation and Tests
 
- [ ] 6.1 Create sandbox developer guides with test credentials, cards, and OTPs:
  - `docs/VNPAY_SANDBOX_GUIDE.md` (NCB test card details & ngrok instructions)
  - `docs/MOMO_SANDBOX_GUIDE.md` (MoMo sandbox test accounts & webhook flow)
  - `docs/STRIPE_TEST_GUIDE.md` (Stripe test card numbers & Stripe CLI webhook listening)
- [ ] 6.2 Add unit and service tests for each gateway adapter (`VnpaySandboxPaymentGatewayTest`, `MomoSandboxPaymentGatewayTest`, `StripeTestPaymentGatewayTest`) with external HTTP calls mocked
- [ ] 6.3 Add callback tests for signature verification, duplicate webhook deduplication, and conflicting status handling
- [ ] 6.4 Add integration tests for end-to-end payment initiation, IPN processing, and email notification dispatch
- [ ] 6.5 Add Bruno collection requests for payment initiation, provider IPN simulation, and status polling
- [ ] 6.6 Run full Maven test suite to ensure clean build and zero regression

