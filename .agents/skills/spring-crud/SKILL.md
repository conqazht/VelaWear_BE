---
name: spring-crud
description: Create or update CRUD feature modules for the VelaWear Spring Boot backend. Use when asked to add a new feature, controller, service, repository, entity, DTOs, filters, basic tests, or API documentation using the project's feature-tree structure, PostgreSQL schema, Lombok rules, ApiResponse wrapper, and no JPA @ManyToMany mappings.
---

# Spring CRUD

Use this skill to implement VelaWear backend CRUD features consistently.

## Workflow

1. Read the required project docs before coding.
2. Check whether the table and endpoints already exist.
3. Follow the feature-tree package structure.
4. Implement in this order: entity, repository, DTOs, service interface, service implementation, controller.
5. Use DTO records, Jakarta validation, `ApiResponse<T>`, constructor injection, and service interface injection.
6. Use Lombok only where allowed by project rules.
7. Do not use JPA `@ManyToMany`; use explicit join entities.
8. Update docs and feature context when behavior or schema changes.
9. Compile or run focused tests when practical.

Read `references/checklist.md` for the detailed CRUD checklist before making broad feature changes.
