## ADDED Requirements

### Requirement: Order Created Email Notification
The system MUST send an order-created transactional email when an order is successfully created.

#### Scenario: Send order confirmation email
- **WHEN** an order is created successfully for a user with an email address
- **THEN** the system sends an order confirmation email containing the order code, receiver information, and payable amount summary

#### Scenario: Order creation remains committed when email fails
- **WHEN** an order is created successfully but the email provider fails
- **THEN** the system preserves the created order and records or logs the notification failure

### Requirement: Payment Result Email Notification
The system MUST send payment result emails when payment state changes to success or failure.

#### Scenario: Send payment success email
- **WHEN** a payment transitions from pending to successful through a verified callback or enabled simulation
- **THEN** the system sends a payment success email for the related order

#### Scenario: Send payment failure email
- **WHEN** a payment transitions from pending to failed through a verified callback or enabled simulation
- **THEN** the system sends a payment failure email for the related order

#### Scenario: Duplicate callback does not duplicate email
- **WHEN** a duplicate callback repeats a payment state transition that was already processed
- **THEN** the system MUST NOT send a duplicate payment result email

### Requirement: Order Completed Email Notification
The system MUST send an order completed or received email when an order reaches the completed lifecycle state.

#### Scenario: Send completed order email
- **WHEN** an order status transitions to `COMPLETED`
- **THEN** the system sends an order completed email to the order user

#### Scenario: No email when status does not change
- **WHEN** an order update keeps the order status unchanged
- **THEN** the system MUST NOT send an order completed email

### Requirement: Notification Delivery Boundary
The system MUST keep notification delivery separate from core order and payment state mutation.

#### Scenario: Email sent through provider abstraction
- **WHEN** a commerce notification must be sent
- **THEN** the system sends it through the existing backend email provider abstraction and MUST NOT call email provider APIs from frontend code

#### Scenario: Notification failure does not roll back finalized payment
- **WHEN** a payment has been marked successful and the payment success email fails
- **THEN** the system keeps the payment and order state committed and records or logs the notification failure

#### Scenario: Notification after committed state change
- **WHEN** an order or payment state transition commits successfully
- **THEN** notification processing occurs after or outside the critical state mutation so failed email delivery cannot leave domain state partially updated
