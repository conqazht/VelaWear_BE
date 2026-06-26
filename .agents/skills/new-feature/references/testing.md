# Testing Checklist

Use this reference before adding or changing tests in VelaWear.

## Read First

- Read the feature source code: entity, repository, service, service implementation, controller, DTOs.
- Read the feature `CONTEXT.md` when it exists.
- Read `docs/PROJECT-RULES.md`.
- Read `docs/API_SPEC.md`.
- Keep endpoint paths consistent with the project convention: `/api/v1/...`.

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

Required setup for `UserServiceImplTest`:

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Module User - UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }
}
```

Required grouping:

```java
@Nested
@DisplayName("Create user")
class CreateUser {
}
```

Required create-user cases:

- Happy path: create user successfully.
- Happy path: password is encoded before saving.
- Duplicate email: throw `DuplicateResourceException` or the project-specific conflict exception.
- Duplicate email: `save()` is not called.
- Verify call order: `existsByEmail()` must be called before `save()`.

Required Mockito checks:

- Mock both `UserRepository` and `PasswordEncoder`.
- Use `argThat()` to verify the saved user has an encoded password.
- Verify saved password is different from the plain request password.
- Use `InOrder` to verify `existsByEmail()` runs before `save()`.
- Use `verify(userRepository, never()).save(any())` when email is duplicated.

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

Use integration tests for HTTP behavior and database persistence.

Required setup:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Module User - UserController")
class UserControllerTest extends AbstractIntegrationTest {
}
```

Rules:

- Use Testcontainers with PostgreSQL through `AbstractIntegrationTest`.
- Do not use H2.
- Use `@Transactional` so each test rolls back database changes.
- Use `tools.jackson.databind.ObjectMapper`.
- Use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Use `userRepository` to verify database state.
- Use JsonPath to verify response body.

Required endpoint scope for the focused user controller test:

- Only test `POST /api/v1/users`.
- Do not mix `GET`, `PUT`, or `DELETE` into this focused test class unless the user asks.

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

Required happy path cases:

- Return `201 Created` when request data is valid.
- Verify response body includes `id`, `name` or `fullName`, and `email`.
- Verify response body does not include `password`.
- Verify user is saved in the database.
- Verify database password is encoded and different from the plain password.

Required validation error cases:

- Blank name returns `400 Bad Request`.
- Blank email returns `400 Bad Request`.
- Invalid email format returns `400 Bad Request`.
- Password shorter than 6 characters returns `400 Bad Request`.
- Verify no user is saved when validation fails.

Required business error cases:

- Existing email returns `409 Conflict`.
- Verify no new user is created when email already exists.

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

## Spring Boot 4 Notes

- Use `tools.jackson.databind.ObjectMapper`.
- Use `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Use Testcontainers PostgreSQL for database-backed integration tests.
- Keep Flyway as the schema source of truth; Hibernate should validate the schema in tests.
