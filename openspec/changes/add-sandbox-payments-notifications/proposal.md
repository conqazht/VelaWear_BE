## Why

The current payment module stores payment records and supports COD/SePay, but does not provide a comprehensive multi-gateway checkout flow (VNPay, MoMo, Stripe), standard sandbox callbacks/IPN verification, or automated transactional emails for order/payment lifecycle events. For this project, sandbox/test integrations demonstrate production-grade payment architecture, secure request signing, and webhook idempotency without touching real money or paid onboarding.

## What Changes

- Add backend-owned payment initiation for sandbox/test providers selected by the frontend: **VNPay, MoMo, Stripe**, along with **SePay (VietQR)** and **COD**.
- Remove ZaloPay to focus cleanly on the primary Vietnamese gateways (VNPay, MoMo) and global card payment (Stripe).
- Add a shared payment orchestration flow with provider-specific **PaymentGateway adapters** (Adapter Pattern), isolating signing, verification, and API calls per gateway.
- Incorporate official **Stripe Java SDK** (`com.stripe:stripe-java`) for Checkout Sessions and `Webhook.constructEvent` signature verification.
- Implement **VNPay HMAC-SHA512** (alphabetical param sorting) and **MoMo HMAC-SHA256** (captureWallet AIO flow) with official IPN response codes (`{"RspCode":"00",...}`).
- Add callback/webhook handlers that verify provider cryptographic signatures and update `Payment`, `PaymentTransaction`, and `Order` state idempotently (deduplicating by provider transaction code and Stripe event ID).
- Align payment expiration windows (15 minutes) with the existing checkout reservation TTL.
- Add development-only simulation endpoints for success/failure callbacks when external sandbox credentials are unconfigured or unavailable.
- Publish domain notification events for order created, payment succeeded, payment failed, and order completed/received, dispatched asynchronously via transactional outbox/email provider.
- Add developer sandbox guide documentation for testing with sandbox credentials and mock bank cards.

## Capabilities

### New Capabilities
- `sandbox-payment-gateways`: Payment initiation, callback/webhook IPN verification, and simulation flows for COD, MoMo, VNPay, SePay (VietQR), and Stripe test mode.
- `commerce-email-notifications`: Transactional emails for order creation, payment result, and order completion/received events.

### Modified Capabilities

## Impact

- Affected APIs: payment initiation endpoint, provider callback/webhook endpoints (`/api/v1/payments/{provider}/callback` and `/ipn`), dev simulation endpoints, and order status transitions.
- Affected domain: `Order`, `Payment`, `PaymentTransaction`, `PaymentProvider` (`COD`, `SEPAY`, `VNPAY`, `MOMO`, `STRIPE`), payment/order status transitions, and raw gateway response storage.
- Affected configuration: sandbox provider endpoints, credentials, return/callback URLs, enabled provider flags, global sandbox environment guard, and dev simulation toggles.
- Affected email system: domain event listeners for order/payment notifications using the existing email provider abstraction.
- Affected tests: gateway adapters, signature signing/verification, IPN idempotency, dev simulation, state transitions, and email event dispatch.
