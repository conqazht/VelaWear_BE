# Testing Checklist

Use this reference before adding or changing tests in VelaWear.

## Read First

- Read `AGENTS.md`, then follow its links to `docs/PROJECT-RULES.md`.
- Read the feature source code: entity, repository, service, service implementation, controller, DTOs.
- Read the feature `CONTEXT.md` when it exists.
- Read `docs/ARCHITECTURE.md` to understand where the feature fits.
- Read `docs/DATABASE.md` to check whether the entity/table already exists.
- Read `docs/PROJECT-RULES.md`.
- Read `docs/API_SPEC.md`.
- Check whether endpoints are already defined before adding or changing tests.
- Keep endpoint paths consistent with the project convention: `/api/v1/...`.
- Ask the user before writing code if requirements, schema, endpoints, or ownership are unclear.

## New Feature Module Workflow

Use this order when creating a new feature module.

1. Understand context:
   - Follow the "Read First" section.
   - Confirm existing schema, endpoints, and module boundaries before implementation.
2. Create package structure:

```text
feature/{feature_name}/
├── {Feature}.java
├── {Feature}Controller.java
├── {Feature}Service.java
├── {Feature}ServiceImpl.java
├── {Feature}Repository.java
└── dto/
    ├── Create{Feature}Request.java
    ├── Update{Feature}Request.java
    └── {Feature}Response.java

src/test/java/.../feature/{feature_name}/
├── {Feature}ServiceImplTest.java
└── {Feature}ControllerTest.java
```

3. Implement in this order:
   - Entity: fields, JPA annotations, relationships, audit fields (`createdAt`, `updatedAt`).
   - Repository: extend `JpaRepository`, add custom query methods only when needed.
   - DTOs: request records with validation, response record with `fromEntity()`.
   - Service interface: method signatures only.
   - Service implementation: business logic, entity/DTO conversion, `@Transactional`.
   - Controller: endpoints, `@Valid`, and `ApiResponse<T>`.
4. Write tests:
   - Unit test service logic in `{Feature}ServiceImplTest.java`.
   - Mock all dependencies: repositories and other services.
   - Integration test the full HTTP flow in `{Feature}ControllerTest.java`.
5. Follow implementation rules:
   - Use constructor injection only (`private final` fields plus explicit constructor).
   - Controllers inject service interfaces, not service implementations.
   - Wrap all controller responses in `ApiResponse<T>`.
   - Add `@Valid` on every `@RequestBody`.
   - Use `FetchType.LAZY` on all relationships.
   - Use project custom exceptions such as `ResourceNotFoundException` and `InvalidRequestException`.
   - Never return entities from controllers; always return DTOs.
6. Update documentation:
   - Update `docs/API_SPEC.md` with new endpoints.
   - Update `docs/DATABASE.md` when adding tables or columns.
   - Create feature `CONTEXT.md` with `/write-context`.
   - Update `docs/PROJECT-STATUS.md` with `/update-status`.
7. Verify:
   - Code compiles with no errors.
   - All old and new tests pass.
   - No file exceeds 300 lines.
   - No method exceeds 50 lines.
   - Check the commit checklist in `docs/PROJECT-RULES.md` section 16.

## Shared Test Style

- Use JUnit 6.
- Test method names must be English and follow `methodName_scenario_expectedResult`.
- Use `@DisplayName` in Vietnamese for test classes, nested groups, and test methods.
- Use `@Nested` to group related cases.
- Use AAA comments in every test:

```java
// Arrange

// Act

// Assert
```

- Keep one behavior per test.
- Prefer AssertJ for Java assertions.
- Prefer JsonPath for HTTP response assertions.
- Do not test implementation details unless the behavior is security-critical, such as password encoding.

## Unit Tests - Service Layer

Use unit tests for service behavior. Unit tests must not hit the database.

Required setup shape for `{Feature}ServiceImplTest`:

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Module Feature - FeatureServiceImpl")
class FeatureServiceImplTest {

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private OtherDependency otherDependency;

    private FeatureServiceImpl featureService;

    @BeforeEach
    void setUp() {
        featureService = new FeatureServiceImpl(featureRepository, otherDependency);
    }
}
```

Required grouping:

```java
@Nested
@DisplayName("Create feature")
class CreateFeature {
}
```

Required cases per service method:

- Create: success.
- Create: validation failure, including duplicate/conflict.
- Create: related entity not found.
- Get by id: success.
- Get by id: not found with `ResourceNotFoundException`.
- Get all: returns list/page with data.
- Get all: returns empty list/page.
- Update: success.
- Update: not found.
- Update: validation failure.
- Delete: success.
- Delete: not found.

Required Mockito checks:

- Mock all direct dependencies only.
- Use `argThat()` when verifying mapped entity state matters.
- Use `InOrder` when call order is part of the behavior.
- Use `verify(repository, never()).save(any())` when validation or conflict prevents persistence.
- Use `assertThrows()` for exception cases.

Example shape:

```java
@Test
@DisplayName("createUser - tạo user thành công và encode password trước khi lưu")
void createUser_validRequest_savesUserWithEncodedPassword() {
    // Arrange
    CreateUserRequest request = new CreateUserRequest(
            "Nguyen Van A",
            "a@example.com",
            "password123",
            LocalDate.of(2000, 1, 1),
            null,
            UserGender.MALE);

    when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    userService.createUser(request);

    // Assert
    verify(userRepository).save(argThat(user ->
            "$2a$10$encoded".equals(user.getPassword())
                    && !"password123".equals(user.getPassword())));

    InOrder inOrder = inOrder(userRepository);
    inOrder.verify(userRepository).existsByEmail("a@example.com");
    inOrder.verify(userRepository).save(any(User.class));
}
```

## Integration Tests - Controller Layer

Use integration tests for the full HTTP flow: request, controller, service, repository, response, security, and database persistence.

Required setup shape:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Module Feature - FeatureController")
class FeatureControllerTest extends AbstractIntegrationTest {
}
```

Rules:

- Use Testcontainers with PostgreSQL through `AbstractIntegrationTest`.
- Do not use H2.
- Use `@Transactional` so each test rolls back database changes.
- Use `tools.jackson.databind.ObjectMapper`.
- Use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Use the feature repository to verify database state.
- Use JsonPath to verify response body structure: `statusCode`, `data`, and `message`.
- Verify content type.

Required endpoint cases:

- `POST`: `201 Created` success.
- `POST`: `400 Bad Request` validation error.
- `POST`: `409 Conflict` duplicate/conflict.
- `POST`: `401 Unauthorized`.
- `GET /{id}`: `200 OK` success.
- `GET /{id}`: `404 Not Found`.
- `GET /{id}`: `401 Unauthorized`.
- `GET`: `200 OK` with pagination.
- `PUT`: `200 OK` success.
- `PUT`: `400 Bad Request` validation error.
- `PUT`: `404 Not Found`.
- `DELETE`: `200 OK` success.
- `DELETE`: `404 Not Found`.

Required grouping:

```java
@Nested
@DisplayName("Happy path")
class HappyPath {
}

@Nested
@DisplayName("Validation errors")
class ValidationErrors {
}

@Nested
@DisplayName("Business errors")
class BusinessErrors {
}
```

Required happy path checks:

- Verify the expected status code.
- Verify response body includes the expected DTO fields.
- Verify response body does not expose sensitive fields.
- Verify persisted database state when the endpoint writes data.

Required validation error checks:

- Required fields return `400 Bad Request` when blank or missing.
- Invalid formats return `400 Bad Request`.
- Invalid lengths or ranges return `400 Bad Request`.
- Verify no data is persisted when validation fails.

Required business error checks:

- Duplicate or conflicting data returns `409 Conflict`.
- Missing resources return `404 Not Found`.
- Unauthorized requests return `401 Unauthorized`.
- Verify no invalid data is persisted when business rules fail.

Example shape:

```java
@Nested
@DisplayName("Happy path")
class HappyPath {

    @Test
    @DisplayName("POST /users - 201: tạo user thành công khi dữ liệu hợp lệ")
    void createUser_validRequest_returnsCreatedUser() throws Exception {
        // Arrange
        CreateUserRequest request = new CreateUserRequest(
                "Nguyen Van A",
                "a@example.com",
                "password123",
                LocalDate.of(2000, 1, 1),
                null,
                UserGender.MALE);

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.email").value("a@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        Optional<User> savedUser = userRepository.findByEmail("a@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getPassword()).isNotEqualTo("password123");
    }
}
```

## What Not To Test

- Entity getters and setters.
- Built-in JPA repository methods such as `findById` and `save`.
- Simple DTO records with no logic.

## Spring Boot 4 Notes

- Use `tools.jackson.databind.ObjectMapper`.
- Use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Use Testcontainers PostgreSQL for database-backed integration tests.
- Keep Flyway as the schema source of truth; Hibernate should validate the schema in tests.
