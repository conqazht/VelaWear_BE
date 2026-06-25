# CRUD Checklist

## Read First

- Read `docs/PROJECT-RULES.md`.
- Read `docs/PROJECT-STATUS.md`.
- Read `docs/ARCHITECTURE.md`.
- Read `docs/DATABASE.md`.
- Read `docs/API_SPEC.md`.
- Read the target feature `CONTEXT.md` when it exists.

## Package Shape

```text
feature/{feature_name}/
├── {Feature}.java
├── {Feature}Controller.java
├── {Feature}Service.java
├── {Feature}ServiceImpl.java
├── {Feature}Repository.java
├── CONTEXT.md
└── dto/
    ├── Create{Feature}Request.java
    ├── Update{Feature}Request.java
    └── {Feature}Response.java
```

## Implementation Order

1. Entity.
2. Repository.
3. DTO records.
4. Service interface.
5. Service implementation.
6. Controller.
7. Tests when behavior has risk.
8. Documentation updates.

## Entity Rules

- Use `@Entity` and explicit `@Table(name = "...")`.
- Use Lombok only where allowed by project rules.
- Do not use Lombok `@Data` on entities.
- Use explicit `@Column(nullable, length, unique)` constraints.
- Use `@Enumerated(EnumType.STRING)` for enum-like values.
- Use `@ManyToOne` and `@OneToMany`; do not use `@ManyToMany`.
- Model join tables as explicit entities with `@EmbeddedId` and `@MapsId`.
- Use `FetchType.LAZY` on relationships.
- Use soft delete when the table has `deleted_at`.

## DTO Rules

- Use Java records for request and response DTOs.
- Request DTOs must use Jakarta validation annotations.
- Response DTOs should expose `static fromEntity()`.
- Never expose entity objects from controllers.
- Never expose password hashes, token hashes, secrets, or internal audit-only fields.

## Service Rules

- Use interface + implementation.
- Put `@Service` on the implementation only.
- Put `@Transactional` on write methods.
- Keep HTTP types out of service code.
- Convert entity to DTO in the service layer.
- Throw `ResourceNotFoundException` for not found.
- Throw `InvalidRequestException` for invalid business requests or conflicts.

## Controller Rules

- Controller receives request, calls service, returns response.
- Inject the service interface.
- Use constructor injection.
- Use `@Valid` on every `@RequestBody`.
- Return `ResponseEntity<ApiResponse<T>>`.
- Do not catch application exceptions in controllers.

## Documentation

- Update `docs/API_SPEC.md` for endpoint changes.
- Update `docs/DATABASE.md` for schema changes.
- Update feature `CONTEXT.md` for non-obvious logic.
- Update `docs/PROJECT-STATUS.md` at the end.
