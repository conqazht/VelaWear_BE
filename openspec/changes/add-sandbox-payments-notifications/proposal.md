## Why

The current payment module stores payment records but does not provide a gateway-like checkout flow, sandbox callbacks, or automated transactional emails for order/payment lifecycle events. For a personal project, sandbox-only integrations can demonstrate production-shaped payment handling without touching real money, live merchant credentials, or paid onboarding.

## What Changes

- Add backend-owned payment initiation for sandbox providers selected by the frontend.
- Add a shared sandbox payment orchestration flow with provider-specific adapters, so core checkout, callback, idempotency, audit, and notification logic is not duplicated per provider.
- Add payment gateway adapters for sandbox MoMo, VNPay, ZaloPay, SePay-style bank transfer, optional Stripe test mode, plus COD as the baseline non-online method.
- Add callback/webhook handlers that verify sandbox payloads where applicable and update `Payment`, `PaymentTransaction`, and `Order` state idempotently.
- Add development-only simulation endpoints or fixtures for success/failure callbacks when sandbox credentials are unavailable.
- Add notification events for order created, payment succeeded, payment failed, and order completed/received.
- Send order/payment/delivery emails through the email provider foundation introduced in phase 1.
- Keep this phase sandbox/dev only; live payment credentials and production money movement are explicitly out of scope.

## Capabilities

### New Capabilities
- `sandbox-payment-gateways`: Sandbox-only payment initiation, callback/webhook handling, and simulated provider flows for COD, MoMo, VNPay, ZaloPay, SePay-style bank transfer, and optional Stripe test mode.
- `commerce-email-notifications`: Transactional emails for order creation, payment result, and order completion/received events.

### Modified Capabilities

## Impact

- Affected APIs: payment initiation endpoint, provider callback/webhook endpoints, optional dev-only simulation endpoints, and order status update behavior.
- Affected domain: `Order`, `Payment`, `PaymentTransaction`, `PaymentProvider`, payment/order status transitions, and gateway raw response storage.
- Affected configuration: sandbox provider endpoints, credentials, return/callback URLs, enabled providers, global sandbox environment guard, and dev simulation guardrails.
- Affected email system: reuse phase 1 email provider abstraction for order/payment/delivery notifications.
- Affected tests: payment initiation, callback verification, idempotency, sandbox simulation, order/payment state updates, and notification event dispatch.
