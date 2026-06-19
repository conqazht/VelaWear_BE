---
name: testing
description: Write or improve tests for VelaWear Spring Boot features. Use when asked to add unit tests, controller integration tests, MockMvc tests, validation/error/security tests, or verify feature behavior according to PROJECT-RULES with PostgreSQL test profile, JUnit 6, Mockito, MockMvc, and Spring Boot 4 imports.
---

# Testing

Use this skill to create focused tests for VelaWear backend features.

## Workflow

1. Read the feature source code and any feature `CONTEXT.md`.
2. Read `docs/PROJECT-RULES.md` and `docs/API_SPEC.md`.
3. Identify happy paths, validation failures, not found cases, conflicts, authorization cases, and side effects.
4. Write service unit tests with Mockito for business behavior.
5. Write controller integration tests with MockMvc when HTTP/security behavior matters.
6. Use PostgreSQL test profile and never add H2.
7. Run focused tests when practical.

Read `references/checklist.md` for coverage requirements and Spring Boot 4 testing notes.
