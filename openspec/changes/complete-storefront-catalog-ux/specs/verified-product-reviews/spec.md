## ADDED Requirements

### Requirement: Privacy-safe public product reviews
The system SHALL expose paged product reviews and aggregate rating summaries without private user or order identifiers.

#### Scenario: Product reviews are read anonymously
- **WHEN** an anonymous shopper requests a product's review page
- **THEN** the response includes display name, product/variant context, rating, comment, ordered images, verified-purchase state, and creation time but no user id or order identifiers

### Requirement: Principal-scoped customer review access
The system SHALL derive customer identity from the authenticated JWT for personal review reads and writes.

#### Scenario: A foreign order item is submitted
- **WHEN** a customer submits an order item owned by another user
- **THEN** the system returns a not-found result and creates no review or files

### Requirement: Completed-order review eligibility
The system SHALL allow at most one review per owned order item only when its order is `COMPLETED`.

#### Scenario: Duplicate review is submitted
- **WHEN** a review already exists for the customer and order item
- **THEN** the system returns conflict and preserves the existing review

### Requirement: Request-bound review media
The system SHALL accept zero to five validated review images as multipart binary data and SHALL NOT accept client-provided file paths.

#### Scenario: Multipart review commits
- **WHEN** the review and all optional files validate and database writes commit
- **THEN** UUID-named images remain available and their ordered URLs are attached to the review

#### Scenario: Review transaction rolls back
- **WHEN** a database or file operation fails after one or more review files were stored
- **THEN** the review transaction rolls back and every stored file from that request is deleted

### Requirement: Orphan review media is reconciled
The system SHALL delete only old review files that have no database reference.

#### Scenario: Reconciliation scans review files
- **WHEN** a file is older than 24 hours and absent from all `review_images` rows
- **THEN** it is deleted while recent or referenced files are retained
