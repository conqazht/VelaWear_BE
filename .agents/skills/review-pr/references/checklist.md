# Review Checklist

## Architecture Fit

- Controller stays thin: receive request, validate, call service, return `ApiResponse`.
- Service owns business logic and transaction boundaries.
- Repository only handles persistence/query concerns.
- Entity is not returned directly from controllers.
- DTO records define API contracts.
- Entity-to-DTO conversion happens in service or mapper layer, not controller.
- Feature package boundaries are respected.
- Extract abstractions only when there are real variants, repeated complexity, or a clear extension point.
- Keep external concerns such as email, payment gateways, file storage, and HTTP clients behind dedicated services/adapters when they appear.

## Security

- No hardcoded secrets, tokens, passwords, API keys, or environment-specific credentials.
- Passwords and refresh tokens are never returned, logged, or stored in raw form.
- JWT claims do not contain sensitive data.
- New endpoints have explicit authorization decisions.
- Public endpoints are intentionally listed in security configuration.
- Validation prevents invalid or unsafe input before business logic.
- Logs do not expose PII or credentials.
- State-changing operations are protected by authentication and role/permission checks where required.

## Naming & Consistency

- Class names follow project conventions: `Controller`, `Service`, `ServiceImpl`, `Repository`, request/response DTOs.
- Method names describe business actions in services and HTTP intent in controllers.
- Variables are meaningful and use camelCase.
- Constants use UPPER_SNAKE_CASE.
- Database names use snake_case and match `docs/DATABASE.md`.
- API paths and DTO names match `docs/API_SPEC.md`.
- Avoid abbreviations that make domain logic harder to read.

## Performance Risks

- Avoid N+1 query patterns, especially loops over lazy relationships.
- Use pagination for list endpoints.
- Use indexes described in `docs/DATABASE.md` for frequent filters and lookups.
- Keep transactions scoped to required business work.
- Avoid unnecessary eager loading.
- Avoid loading full entities when lightweight projections or DTO queries are clearly better and already justified by the use case.
- Avoid repeated expensive computation in request loops.
- Cleanup jobs and soft-delete queries must use indexed columns.

## Optional Review Lenses

Apply these only when the changed code shows matching complexity. Do not force them onto simple CRUD.

### SOLID

Use this lens when a class/service is growing large, has many reasons to change, or mixes unrelated responsibilities.

- Single Responsibility: split only when responsibilities are genuinely separate.
- Open/Closed: consider extension points for real variants such as payment providers, shipping rules, discount rules, or email providers.
- Interface Segregation: avoid interfaces that force unrelated methods together.
- Dependency Inversion: depend on abstractions when infrastructure details would otherwise leak into business logic.

### Clean Architecture

Use this lens when business rules start depending on framework/infrastructure details.

- Keep core business flow in service/application code, not controllers or infrastructure adapters.
- Put gateway/email/file/payment details behind dedicated services/adapters.
- Avoid letting JPA entity shape dictate public API shape.
- Keep feature boundaries clear before introducing shared abstractions.

## VelaWear Rules

- Use Java records for DTOs.
- Use constructor injection only.
- Use `@Transactional` on write service methods.
- Use `@Enumerated(EnumType.STRING)`.
- Do not use JPA `@ManyToMany`; model join tables explicitly.
- Do not use Lombok `@Data` on entities.
- Use custom exceptions such as `ResourceNotFoundException` and `InvalidRequestException`.

## Tests & Docs

- Risky behavior has focused tests.
- Integration tests use PostgreSQL `test` profile.
- Security/authorization changes have tests or a clear manual verification note.
- Update `docs/API_SPEC.md` for endpoint changes.
- Update `docs/DATABASE.md` for schema changes.
- Update feature `CONTEXT.md` for important logic.

## Response Format

Lead with findings, ordered by severity.

Use:
1. Blockers.
2. Suggestions.
3. Good parts.
4. Test gaps or residual risk.
