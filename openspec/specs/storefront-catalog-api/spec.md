# storefront-catalog-api Specification

## Purpose
TBD - created by archiving change complete-storefront-catalog-ux. Update Purpose after archive.

## Requirements

### Requirement: Public storefront catalog query
The system SHALL expose a public storefront product endpoint with validated search, multi-value facets, effective-price bounds, deterministic sorting, and one-based pagination.

#### Scenario: Multiple facet values are supplied
- **WHEN** a request contains multiple category slugs, color ids, or size ids
- **THEN** values within each facet use OR while different facets use AND

#### Scenario: Variant constraints are combined
- **WHEN** color, size, and price filters are supplied together
- **THEN** a product matches only if one active non-deleted variant satisfies all selected variant constraints

### Requirement: Effective pricing drives storefront results
The system SHALL use the canonical variant pricing service for price filtering, representative pricing, facets, and ordering.

#### Scenario: Campaign changes displayed price
- **WHEN** an eligible Standard or Flash campaign changes a variant's effective price
- **THEN** the product is filtered and ordered using that effective price rather than base price

### Requirement: Storefront facets are coherent
The system SHALL return active category, color, size, and price-range facets with distinct product counts derived from the same pricing snapshot as the result page.

#### Scenario: A facet is already selected
- **WHEN** category, color, or size selections are active
- **THEN** each facet's counts ignore its own selection while honoring all other active constraints and retain zero-count options

### Requirement: Storefront sorts are deterministic
The system SHALL support `featured`, `newest`, `price-asc`, and `price-desc` before result slicing.

#### Scenario: Featured products tie
- **WHEN** two products have equal promotion, discount, review, and creation signals
- **THEN** the id tie-breaker produces stable pagination without duplicate or missing products
