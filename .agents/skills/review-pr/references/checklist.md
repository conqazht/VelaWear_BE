# Review Checklist

Use this as an internal checklist while reviewing VelaWear backend changes. Report only real findings, ordered by severity.

## Architecture Fit

- [ ] Controller stays thin: receive request, validate, call service, return `ApiResponse`.
- [ ] Controller does not contain business logic.
- [ ] Service interface plus `ServiceImpl` pattern is preserved.
- [ ] Controller injects the service interface, not the implementation class.
- [ ] Service layer has no HTTP concerns such as `HttpServletRequest`, `ResponseEntity`, or `HttpStatus`.
- [ ] Service owns business logic and transaction boundaries.
- [ ] Repository only handles persistence/query concerns.
- [ ] Entity is not returned directly from controllers.
- [ ] DTO records define API contracts.
- [ ] Entity-to-DTO conversion happens in the service or mapper layer, not controller.
- [ ] Feature package boundaries are respected.
- [ ] Extract abstractions only when there are real variants, repeated complexity, or a clear extension point.
- [ ] External concerns such as email, payment gateways, file storage, and HTTP clients stay behind dedicated services/adapters when they appear.

## Type Safety & DTOs

- [ ] Request and response DTOs use Java records.
- [ ] Every `@RequestBody` that needs validation has `@Valid`.
- [ ] Request DTOs use Jakarta Validation annotations such as `@NotBlank`, `@Email`, `@Size`, `@Min`, and `@Max`.
- [ ] Response DTOs expose only API-safe fields.
- [ ] Response DTOs provide `fromEntity()` when that is the local feature pattern.
- [ ] Entities are never used as request or response contracts.
- [ ] Sensitive fields such as password, refresh token, access token, internal notes, or secrets are not exposed.
- [ ] Controller responses are wrapped in `ApiResponse<T>`.

## Dependency Injection

- [ ] Constructor injection is used; no field injection with `@Autowired`.
- [ ] Dependencies are `private final`.
- [ ] Use an explicit constructor or Lombok `@RequiredArgsConstructor` consistently with the surrounding feature code.
- [ ] Only import Lombok annotations that are actually used.
- [ ] No new circular dependency is introduced between features or services.

## Error Handling

- [ ] Custom project exceptions are used instead of raw `RuntimeException`.
- [ ] 404 cases throw `ResourceNotFoundException`.
- [ ] Invalid request, ownership, state, and conflict cases use `InvalidRequestException` or another project-specific exception.
- [ ] Controller does not add local `try/catch` for normal business errors; global exception handling owns response mapping.
- [ ] Validation failures return the existing project error response shape.
- [ ] Exception messages are useful for API consumers but do not leak secrets or internals.

## Security

- [ ] No hardcoded secrets, tokens, passwords, API keys, or environment-specific credentials.
- [ ] Passwords and refresh tokens are never returned, logged, or stored in raw form.
- [ ] JWT claims do not contain sensitive data.
- [ ] New endpoints have explicit authorization decisions.
- [ ] Public endpoints are intentionally listed in security configuration.
- [ ] Validation prevents invalid or unsafe input before business logic.
- [ ] Logs do not expose PII or credentials.
- [ ] State-changing operations are protected by authentication and role/permission checks where required.
- [ ] Ownership checks prevent one user from reading or mutating another user's data.

## JPA & Database

- [ ] Relationships use `FetchType.LAZY`.
- [ ] Enums use `@Enumerated(EnumType.STRING)`, never ordinal mapping.
- [ ] Write service methods use `@Transactional`.
- [ ] Read-only service methods use `@Transactional(readOnly = true)` when useful and consistent with nearby code.
- [ ] Repository methods return `Optional<T>` for single nullable results.
- [ ] No JPA `@ManyToMany`; join tables are modeled explicitly.
- [ ] `@Column` constraints such as nullable, length, and unique match the database schema.
- [ ] Flyway migrations are the source of truth for schema changes.
- [ ] New or changed tables/columns are reflected in `docs/DATABASE.md`.
- [ ] Frequent filters, joins, cleanup jobs, and soft-delete queries have appropriate indexes.

## Naming & Consistency

- [ ] Class names follow project conventions: `Controller`, `Service`, `ServiceImpl`, `Repository`, request DTO, response DTO.
- [ ] Method names describe business actions in services and HTTP intent in controllers.
- [ ] Variables are meaningful and use camelCase.
- [ ] Constants use UPPER_SNAKE_CASE.
- [ ] Database names use snake_case and match `docs/DATABASE.md`.
- [ ] API paths, request DTOs, and response DTOs match `docs/API_SPEC.md`.
- [ ] Package names match the feature structure used by neighboring modules.
- [ ] Avoid abbreviations that make domain logic harder to read.

## Performance Risks

- [ ] Avoid N+1 query patterns, especially loops over lazy relationships.
- [ ] Use pagination for list endpoints.
- [ ] Use indexes described in `docs/DATABASE.md` for frequent filters and lookups.
- [ ] Keep transactions scoped to required business work.
- [ ] Avoid unnecessary eager loading.
- [ ] Avoid loading full entities when lightweight projections or DTO queries are clearly better and already justified by the use case.
- [ ] Avoid repeated expensive computation in request loops.
- [ ] Cleanup jobs and soft-delete queries use indexed columns.

## Code Quality

- [ ] Files stay under 300 lines unless there is a clear project-approved reason.
- [ ] Methods stay under 50 lines unless complexity is genuinely unavoidable.
- [ ] Entities do not use Lombok `@Data`.
- [ ] Logger usage follows surrounding project style.
- [ ] Log statements use `{}` placeholders rather than string concatenation.
- [ ] Logs omit sensitive data.
- [ ] No unused imports, dead code, commented-out code, or debug prints remain.
- [ ] Magic strings or numbers are extracted when reused or domain-significant.
- [ ] Broad `catch` blocks do not swallow errors or hide failed business operations.

## Optional Review Lenses

Apply these only when the changed code shows matching complexity. Do not force them onto simple CRUD.

### SOLID

Use this lens when a class/service is growing large, has many reasons to change, or mixes unrelated responsibilities.

- [ ] Single Responsibility: split only when responsibilities are genuinely separate.
- [ ] Open/Closed: consider extension points for real variants such as payment providers, shipping rules, discount rules, or email providers.
- [ ] Interface Segregation: avoid interfaces that force unrelated methods together.
- [ ] Dependency Inversion: depend on abstractions when infrastructure details would otherwise leak into business logic.

### Clean Architecture

Use this lens when business rules start depending on framework/infrastructure details.

- [ ] Core business flow stays in service/application code, not controllers or infrastructure adapters.
- [ ] Gateway, email, file, payment, and external HTTP details stay behind dedicated services/adapters.
- [ ] JPA entity shape does not dictate public API shape.
- [ ] Feature boundaries are clear before introducing shared abstractions.

## VelaWear Rules

- [ ] Read `docs/PROJECT-RULES.md` before making final review claims.
- [ ] Use Java records for DTOs.
- [ ] Use constructor injection only.
- [ ] Use `@Transactional` on write service methods.
- [ ] Use `@Enumerated(EnumType.STRING)`.
- [ ] Do not use JPA `@ManyToMany`; model join tables explicitly.
- [ ] Prefer Lombok annotations such as `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@NoArgsConstructor`, and `@Builder` where the project already uses them.
- [ ] Do not use Lombok `@Data` on entities.
- [ ] Use custom exceptions such as `ResourceNotFoundException` and `InvalidRequestException`.

## Tests & Docs

- [ ] Risky behavior has focused tests.
- [ ] Unit tests cover service logic with mocked dependencies.
- [ ] Integration tests cover full HTTP flow when controller behavior changes.
- [ ] Integration tests use PostgreSQL `test` profile.
- [ ] Security/authorization changes have tests or a clear manual verification note.
- [ ] Error-path behavior has tests for not found, validation, conflict, and unauthorized cases when applicable.
- [ ] New endpoint behavior is reflected in `docs/API_SPEC.md`.
- [ ] Schema changes are reflected in `docs/DATABASE.md`.
- [ ] New features include feature-package `CONTEXT.md`.
- [ ] Important logic changes update feature `CONTEXT.md` with a refactor/change note instead of overwriting history.
- [ ] `docs/PROJECT-STATUS.md` is updated when the change affects project progress.

## Response Format

Lead with findings, ordered by severity.

- [ ] Blockers: must fix before merge.
- [ ] Suggestions: improve quality but are not blocking.
- [ ] Good parts: mention what was done well when useful.
- [ ] Test gaps or residual risk: state what was not verified.
- [ ] If no issues are found, say that clearly and mention remaining risk or test gaps.
