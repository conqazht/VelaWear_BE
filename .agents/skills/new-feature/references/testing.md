# Testing Checklist

## Read First

- Read the feature source code: entity, repository, service, service implementation, controller, DTOs.
- Read the feature `CONTEXT.md` when it exists.
- Read `docs/PROJECT-RULES.md`.
- Read `docs/API_SPEC.md`.

## Unit Tests

Use unit tests for service behavior.

```java
@ExtendWith(MockitoExtension.class)
class FeatureServiceImplTest {
    @Mock
    private FeatureRepository featureRepository;

    @InjectMocks
    private FeatureServiceImpl featureService;
}
```

Required coverage:

- Create success.
- Create duplicate/conflict.
- Related entity not found.
- Get by id success.
- Get by id not found.
- List returns data.
- List returns empty page/list.
- Update success.
- Update not found.
- Update conflict.
- Delete success.
- Delete not found.

Rules:

- Mock only direct dependencies.
- Test one behavior per method.
- Use `methodName_scenario_expectedResult`.
- Add `@DisplayName("Should ...")` to every test.
- Use `assertThrows()` for exception cases.
- Verify important interactions with `verify()`.
- Do not hit the database in unit tests.

## Integration Tests

Use integration tests for HTTP behavior and security.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureControllerTest {
}
```

Required endpoint coverage:

- `POST`: success, validation error, duplicate/conflict, unauthorized.
- `GET /{id}`: success, not found, unauthorized.
- `GET`: success, pagination, empty result.
- `PUT`: success, validation error, not found, unauthorized.
- `DELETE`: success, not found, unauthorized.

Rules:

- Use Testcontainers with PostgreSQL, not H2.
- Use `@ActiveProfiles("test")` on integration tests.
- Verify status code and `ApiResponse` fields.
- Keep tests independent from execution order.
- Do not call external services.

## Spring Boot 4 Notes

- Use `tools.jackson.databind.ObjectMapper`.
- Use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
