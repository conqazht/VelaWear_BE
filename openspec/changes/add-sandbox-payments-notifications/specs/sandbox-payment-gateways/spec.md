## ADDED Requirements

### Requirement: Sandbox Payment Initiation
The system MUST allow the backend to initiate a payment for an existing order using a frontend-selected provider while keeping provider secrets and gateway API calls inside the backend.

#### Scenario: Initiate MoMo sandbox payment
- **WHEN** a client requests payment initiation for an eligible order with provider `MOMO`
- **THEN** the system creates or reuses a pending payment, calls the configured MoMo sandbox gateway, and returns a redirect URL or payment session payload to the client

#### Scenario: Initiate VNPay sandbox payment
- **WHEN** a client requests payment initiation for an eligible order with provider `VNPAY`
- **THEN** the system creates or reuses a pending payment, creates a signed VNPay sandbox payment URL, and returns it to the client

#### Scenario: Initiate SePay bank transfer sandbox payment
- **WHEN** a client requests payment initiation for an eligible order with provider `SEPAY`
- **THEN** the system creates or reuses a pending payment with provider `SEPAY` and returns QR or bank-transfer reference data for the order

#### Scenario: Initiate COD payment
- **WHEN** a client requests payment initiation for an eligible order with provider `COD`
- **THEN** the system records the COD payment state without calling an external gateway

### Requirement: Gateway Configuration
The system MUST keep sandbox provider endpoints, credentials, return URLs, callback URLs, and enabled-provider flags in backend configuration.

#### Scenario: Provider disabled
- **WHEN** a client requests initiation for a provider disabled by configuration
- **THEN** the system rejects the request and MUST NOT create a gateway payment session

#### Scenario: Sandbox environment configured
- **WHEN** the application starts with sandbox payment configuration
- **THEN** provider adapters use sandbox endpoints and sandbox credentials instead of production endpoints

### Requirement: Payment Callback Processing
The system MUST process provider callback, webhook, IPN, or simulation payloads in the backend and update payment/order state from verified results.

#### Scenario: Successful provider callback
- **WHEN** the backend receives a verified success callback for a pending payment
- **THEN** the system stores a payment transaction, marks the payment successful, sets the order payment status to `PAID`, and records the paid timestamp

#### Scenario: Failed provider callback
- **WHEN** the backend receives a verified failure callback for a pending payment
- **THEN** the system stores a payment transaction, marks the payment failed, and sets the order payment status to `FAILED`

#### Scenario: Invalid provider signature
- **WHEN** the backend receives a callback with an invalid provider signature or checksum
- **THEN** the system rejects the callback and MUST NOT change payment or order state

#### Scenario: Browser return is not authoritative
- **WHEN** the frontend returns from a payment provider through a browser return URL
- **THEN** the system MUST NOT mark the payment successful from frontend-controlled data alone

### Requirement: Payment Idempotency
The system MUST handle duplicate callbacks and simulations idempotently.

#### Scenario: Duplicate success callback
- **WHEN** the backend receives the same successful provider transaction more than once
- **THEN** the system MUST NOT duplicate state transitions or send duplicate success notifications

#### Scenario: Already finalized payment
- **WHEN** a callback arrives for a payment already finalized with the same terminal status
- **THEN** the system returns an idempotent success response and preserves existing payment/order state

#### Scenario: Conflicting terminal callback
- **WHEN** a callback attempts to change a payment from one terminal status to a conflicting terminal status
- **THEN** the system records the gateway response for audit and rejects or flags the conflict without silently overwriting the finalized payment

### Requirement: Payment Transaction Audit
The system MUST store provider callback/simulation data as payment transaction audit records.

#### Scenario: Store raw gateway response
- **WHEN** a provider callback, webhook, IPN, or simulation is processed
- **THEN** the system stores the raw gateway response or normalized simulation payload in `payment_transactions.gateway_response`

#### Scenario: Store provider transaction code
- **WHEN** the provider supplies a transaction identifier
- **THEN** the system stores that identifier as the transaction code and uses it for idempotency where possible

### Requirement: Dev/Test Payment Simulation
The system MUST provide payment success and failure simulation only when explicitly enabled for dev or test use.

#### Scenario: Simulate payment success in dev
- **WHEN** simulation is enabled and a developer submits a success simulation for a pending payment
- **THEN** the system processes it through the same state-transition path as a verified provider success

#### Scenario: Simulate payment failure in dev
- **WHEN** simulation is enabled and a developer submits a failure simulation for a pending payment
- **THEN** the system processes it through the same state-transition path as a verified provider failure

#### Scenario: Simulation disabled
- **WHEN** simulation is disabled by profile or configuration
- **THEN** the system rejects simulation requests and MUST NOT change payment or order state
