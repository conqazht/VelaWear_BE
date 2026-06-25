---
name: new-feature
description: Create or update VelaWear Spring Boot backend features end to end. Use when adding or changing a feature module, controller, service, repository, entity, DTOs, filters, API docs, CRUD behavior, or focused tests with PostgreSQL, JUnit 6, Mockito, MockMvc, and Spring Boot 4 conventions.
---

# New Feature

Use this skill to build or update VelaWear backend features consistently, including implementation and focused tests.

## Workflow

1. Read `docs/PROJECT-RULES.md`, `docs/PROJECT-STATUS.md`, `docs/ARCHITECTURE.md`, `docs/DATABASE.md`, and `docs/API_SPEC.md`.
2. Read the target feature `CONTEXT.md` when it exists.
3. Check whether the schema, endpoints, and feature package already exist.
4. Implement feature code in this order: entity, repository, DTOs, service interface, service implementation, controller.
5. Add or update tests based on behavior risk and API surface.
6. Update API, database, feature context, and project status docs when behavior or schema changes.
7. Compile or run focused tests when practical.

## References

- Read `references/crud.md` before broad feature implementation.
- Read `references/testing.md` before adding or changing tests.

## Hard Rules

- Use DTO records, Jakarta validation, `ApiResponse<T>`, constructor injection, and service interface injection.
- Do not use JPA `@ManyToMany`; model join tables explicitly.
- Use PostgreSQL for integration tests; do not add H2.
- Keep HTTP concerns out of service code.
