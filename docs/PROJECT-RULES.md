# Project Rules — Spring Boot 4 + JWT (oauth2-resource-server)

> Coding conventions and best practices. Both AI and developer must follow.
> This is the single source of truth — all AI tools (Claude, Cursor, Copilot, Gemini) read this file.

---

## 0. Tech Stack

| Layer | Technology                                                  |
|-------|-------------------------------------------------------------|
| Language | Java 25                                                     |
| Framework | Spring Boot 4.x / Spring Framework 7                        |
| Security | Spring Security 7 + oauth2-resource-server (Nimbus JOSE JWT) |
| Database | Spring Data JPA + PostgreSQL                                |
| Build | Maven                                                       |
| Test | JUnit 6 + Mockito + MockMvc                                 |
| API Docs | SpringDoc OpenAPI (Swagger)                                 |
| Validation | Jakarta Bean Validation                                     |
| Boilerplate | Lombok                                                     |

---

## 1. Package Structure

```
com.example.projectname/
│
├── ProjectNameApplication.java
│
├── config/                              # All configuration classes
│   ├── SecurityConfig.java              # SecurityFilterChain + oauth2ResourceServer
│   ├── JwtConfig.java                   # JwtEncoder, JwtDecoder, Secret key (HMAC HS512)
│   ├── CorsConfig.java
│   └── OpenApiConfig.java
│
├── security/                            # Security-related components
│   ├── SecurityUtil.java                # Helper: get current user from SecurityContext
│   └── CustomUserDetailsService.java    # Loads user from DB for authentication
│
├── exception/                           # Global exception handling
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice
│   ├── AppException.java                # Base custom exception
│   ├── ResourceNotFoundException.java
│   └── InvalidRequestException.java
│
├── dto/                                 # Shared DTOs
│   ├── ApiResponse.java                 # Standard response wrapper
│   └── PaginationDTO.java              # If needed
│
├── feature/                             # Business features
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── AuthService.java             # Interface
│   │   ├── AuthServiceImpl.java         # Implementation
│   │   └── dto/
│   │       ├── LoginRequest.java
│   │       ├── RegisterRequest.java
│   │       └── TokenResponse.java
│   │
│   ├── user/
│   │   ├── UserController.java
│   │   ├── UserService.java             # Interface
│   │   ├── UserServiceImpl.java         # Implementation
│   │   ├── UserRepository.java
│   │   ├── User.java                    # Entity
│   │   └── dto/
│   │       ├── UserResponse.java
│   │       └── UpdateUserRequest.java
│   │
│   └── [other features]/
│       ├── CONTEXT.md                   # Implementation context per module
│       └── ...
│
├── util/                                # Utility classes (pure static, no state)
│
└── resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── application-test.yml      # Test profile — overrides DB, JWT, etc. for testing
```

### Package Rules
- 1 feature = 1 package containing controller, service (interface + impl), repository, entity, DTOs
- Feature DTOs stay inside the feature's `dto/` sub-package, NOT in the shared `dto/`
- Shared `dto/` only contains cross-cutting DTOs like `ApiResponse`
- `config/` and `security/` are top-level — they serve the entire application
- Test files mirror the same package structure under `src/test/java`

---

## 2. Naming Convention

### Classes
| Type | Pattern | Example |
|---|---|---|
| Entity | `[Name]` | `User`, `Payment`, `Order` |
| Controller | `[Feature]Controller` | `AuthController`, `UserController` |
| Service interface | `[Feature]Service` | `UserService`, `AuthService` |
| Service impl | `[Feature]ServiceImpl` | `UserServiceImpl`, `AuthServiceImpl` |
| Repository | `[Feature]Repository` | `UserRepository` |
| DTO request | `[Action/Feature]Request` | `LoginRequest`, `CreateOrderRequest` |
| DTO response | `[Feature]Response` | `UserResponse`, `TokenResponse` |
| Exception | `[Name]Exception` | `ResourceNotFoundException` |
| Config | `[Feature]Config` | `SecurityConfig`, `JwtConfig` |

### Methods
| Layer | Convention | Example |
|---|---|---|
| Controller | HTTP verb-oriented | `getUser()`, `createUser()`, `deleteUser()` |
| Service | Business action | `authenticate()`, `register()`, `processPayment()` |
| Repository | Spring Data convention | `findByEmail()`, `existsByUsername()` |

### Variables
- `camelCase` for all variables and methods
- `UPPER_SNAKE_CASE` for constants
- Meaningful names, avoid abbreviations: `userRepository` not `userRepo` or `ur`
- Boolean prefix: `is/has/can` — `isActive`, `hasPermission`, `canDelete`

---

## 3. Service Layer — Interface + Implementation

### Interface
```java
public interface UserService {
    UserResponse getUserById(long id);
    UserResponse createUser(CreateUserRequest request);
    UserResponse updateUser(long id, UpdateUserRequest request);
    void deleteUser(long id);
    List<UserResponse> getAllUsers();
}
```

### Implementation
```java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse getUserById(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new InvalidRequestException("Email already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        return UserResponse.fromEntity(userRepository.save(user));
    }
}
```

### Rules
- Service interface defines the contract — NO implementation details
- Interface contains ONLY method signatures, no default methods (unless shared logic)
- `@Service` annotation on Impl class, NOT on interface
- `@Transactional` on Impl methods that write data
- Class-level `@Transactional(readOnly = true)` is optional — use method-level for clarity
- Controller depends on interface: `private final UserService userService`
- Service MUST NOT know about HTTP (no HttpServletRequest, ResponseEntity, HttpStatus)
- Entity → DTO conversion happens in service layer

---

## 4. Controller Layer

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;  // Interface, not Impl

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @RequestBody @Valid CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

### Rules
- Controller does exactly 3 things: receive request → call service → return response
- NO business logic in controller
- ALWAYS use `@Valid` on `@RequestBody`
- ALWAYS return `ResponseEntity<ApiResponse<T>>`
- ALWAYS use constructor injection — declare `final` fields, write explicit constructor
- NEVER use `@Autowired` field injection
- Inject interface, NOT implementation class

---

## 5. ApiResponse Wrapper

```java
public record ApiResponse<T>(
        int statusCode,
        T data,
        String message,
        LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), data, "Success", LocalDateTime.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), data, "Created", LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return new ApiResponse<>(statusCode, null, message, LocalDateTime.now());
    }
}
```

### Rules
- ALL endpoints return `ApiResponse<T>` — no exceptions
- Use existing factory methods (`success()`, `error()`) — do NOT construct manually in controllers
- Error responses also go through `ApiResponse.error()` (via GlobalExceptionHandler)
- If your existing ApiResponse has different fields, follow your existing structure consistently

---

## 6. DTO Rules — Java Records

```java
// Request DTO — with Jakarta validation
public record CreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        String password,

        @NotBlank(message = "Name is required")
        String name
) {}

// Response DTO — with factory method from entity
public record UserResponse(
        long id,
        String email,
        String name,
        LocalDateTime createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt()
        );
    }
}
```

### Rules
- Use `record` for all DTOs — immutable by design
- Request DTOs: ALWAYS have Jakarta Bean Validation annotations
- Response DTOs: have `static fromEntity()` conversion method
- NEVER expose Entity directly outside the service layer
- NEVER put sensitive fields (password, token secrets) in response DTOs
- NEVER put `@Entity` or JPA annotations on DTOs

---

## 7. Entity Rules

```java
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;    // BCrypt hash only, NEVER plain text

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    private boolean isActive = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
```

### Rules
- Explicit `@Table(name = "...")` with snake_case table name
- Explicit `@Column(nullable, length, unique)` constraints
- `@Enumerated(EnumType.STRING)` — NEVER use ORDINAL
- Use `Instant` or `LocalDateTime` — NEVER `java.util.Date` or `Timestamp`
- Use Lombok `@Getter`, `@Setter`, and `@NoArgsConstructor` for JPA boilerplate
- Do NOT use Lombok `@Data` on entities — it generates `equals/hashCode` on all fields, causing JPA issues
- Do NOT use `@ManyToMany` in JPA entities. Model join tables as explicit entities, for example `UserRole` and `PermissionRole`, then use `@ManyToOne` from the join entity and `@OneToMany` from the aggregate side.
- Join entities with composite keys must use `@Embeddable` id classes or `@EmbeddedId` + `@MapsId` consistently.
- `createdAt` / `updatedAt`: prefer getter-only fields or do not set them manually in business code because Hibernate manages them
- Password: ALWAYS stored as BCrypt hash

---

## 8. JWT with oauth2-resource-server

### Approach
Spring Security's built-in oauth2-resource-server handles JWT validation automatically.
No custom `OncePerRequestFilter` needed. We provide `JwtEncoder` (sign/create tokens)
and `JwtDecoder` (verify tokens) as Spring beans. Algorithm: **HS512** (symmetric secret key).

### Maven Dependencies
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<!-- spring-security-oauth2-jose is included transitively (Nimbus JOSE JWT) -->
```

### application.yml
```yaml
jwt:
  secret-key: ${JWT_SECRET_KEY}        # Min 64 chars for HS512 — load from env variable
  access-token-expiration: 900         # 15 minutes (in seconds)
  refresh-token-expiration: 604800     # 7 days (in seconds)
```

### JwtProperties.java (record-based config binding)
```java
@ConfigurationProperties("jwt")
public record JwtProperties(
        String secretKey,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {}
```

### JwtConfig.java
```java
@Configuration
public class JwtConfig {

    private final JwtProperties jwtProperties;

    public JwtConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKey key = new SecretKeySpec(
                jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(key);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(
                jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}
```

### SecurityConfig.java
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)      // Stateless JWT — no CSRF needed
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))     // Uses JwtDecoder bean
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Getting Current User in Controller
```java
// Option 1: From @AuthenticationPrincipal
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
        @AuthenticationPrincipal Jwt jwt) {
    String email = jwt.getSubject();
    long userId = jwt.getClaim("userId");
    // ...
}

// Option 2: SecurityUtil helper
public class SecurityUtil {
    public static Optional<String> getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.ofNullable(jwtAuth.getToken().getSubject());
        }
        return Optional.empty();
    }
}
```

### JWT Security Rules
- Access token: **15 minutes** — short-lived to minimize leak risk
- Refresh token: **7 days** — store in DB, can be revoked
- CSRF disabled ONLY because we use stateless JWT — if using cookie-based auth, re-enable CSRF
- Spring's `oauth2ResourceServer` handles token validation automatically — no custom filter needed

---

## 9. Exception Handling

```java
// Base exception — Lombok @Getter is allowed
public class AppException extends RuntimeException {
    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

// Specific exceptions
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super("%s not found with %s: %s".formatted(resource, field, value),
              HttpStatus.NOT_FOUND);
    }
}

public class InvalidRequestException extends AppException {
    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

// Global handler — SLF4J logger declared manually
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (a, b) -> a));
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(400, errors, "Validation failed", LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);  // Full stack trace in logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Internal server error"));  // Generic to client
    }
}
```

### Rules
- All custom exceptions extend `AppException`
- `@RestControllerAdvice` handles ALL exceptions — controllers do NOT try/catch
- Validation errors return field → message map
- Unexpected errors: log full stack trace, but return only generic message to client
- NEVER expose stack traces, SQL errors, or internal details to client

---

## 10. Repository Layer

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
    List<User> findActiveByRole(@Param("role") Role role);
}
```

### Rules
- Use Spring Data derived query methods when possible
- `@Query` with JPQL for complex queries — avoid native queries unless necessary
- Return `Optional<T>` for single results — NEVER return null
- No business logic in repository

---

## 11. API Versioning (Spring Boot 4)

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping(version = "1")
    public ResponseEntity<ApiResponse<List<UserResponseV1>>> getUsersV1() { ... }

    @GetMapping(version = "2")
    public ResponseEntity<ApiResponse<List<UserResponseV2>>> getUsersV2() { ... }
}
```

### Rules
- Use `version` attribute in `@RequestMapping` (Spring Boot 4 built-in)
- Each version has its own DTO: `UserResponseV1`, `UserResponseV2`
- Maintain backward compatibility for at least 2 versions
- Deprecate old versions before removing

---

## 12. Spring Boot 4 Specifics

### Null Safety (JSpecify)
```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public @NonNull UserResponse getUserById(@NonNull Long id) { ... }
public @Nullable User findByEmail(@NonNull String email) { ... }
```

### HTTP Service Client (external API calls)
```java
@HttpExchange(url = "${external.api.base-url}")
public interface ExternalApiClient {

    @PostExchange("/charges")
    ChargeResponse createCharge(@RequestBody ChargeRequest request);
}
```
- Use `@HttpExchange` for external service calls — replaces RestTemplate boilerplate

### Record-based Configuration
```java
@ConfigurationProperties("app.payment")
public record PaymentProperties(
        String apiKey,
        String webhookSecret,
        int maxRetries
) {}
```

---

## 13. Testing

```java
// Unit test — Service layer (mock dependencies)
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void getUserById_notFound_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserById(1L));
    }

    @Test
    @DisplayName("Should return user when found")
    void getUserById_found_returnsUserResponse() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setName("Test");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.getUserById(1L);

        assertEquals("test@test.com", result.email());
    }
}

// Integration test — Controller layer (full Spring context, test profile)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")           // REQUIRED on ALL integration tests
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 200 with tokens when login is valid")
    void login_validCredentials_returnsTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
```

### application-test.yml (bắt buộc có)
```yaml
# src/main/resources/application-test.yml
spring:
  datasource:
    url: ${TEST_DB_URL}           # PostgreSQL test database — NOT H2
    username: ${TEST_DB_USERNAME}
    password: ${TEST_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway là nguồn schema chính, Hibernate chỉ validate
  flyway:
    enabled: true

jwt:
  secret-key: ${TEST_JWT_SECRET_KEY}
  access-token-expiration: 900
  refresh-token-expiration: 604800
```

### Rules
- Test naming: `[method]_[scenario]_[expected]`
- `@DisplayName` on every test — describe behavior, not implementation
- Unit tests: `@ExtendWith(MockitoExtension.class)`, mock dependencies
- Integration tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- Always test: happy path + validation error + not found + unauthorized
- **TUYỆT ĐỐI KHÔNG dùng H2 in-memory database** — không thêm H2 vào `pom.xml`, kể cả scope `test`
- **Chạy test bằng profile `test`** — tất cả integration test PHẢI có `@ActiveProfiles("test")`
- Database cho test: PostgreSQL riêng (local hoặc CI), cấu hình qua `application-test.yml`
- Flyway chạy trong test profile; Hibernate chỉ validate schema, không tự tạo/xóa schema
- Biến môi trường test (`TEST_DB_URL`, v.v.) phải được set trước khi chạy test

### ⚠️ Spring Boot 4 — Breaking Changes in Tests

| What | Old (Spring Boot 3) | New (Spring Boot 4) |
|------|--------------------|--------------------|
| `ObjectMapper` import | `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `@AutoConfigureMockMvc` import | `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |

- **Jackson 3.x**: package `com.fasterxml.jackson` đã đổi thành `tools.jackson` — cập nhật toàn bộ import `ObjectMapper` trong test files.
- **`@AutoConfigureMockMvc`**: đã chuyển sang package mới — IDE có thể không tự resolve đúng, kiểm tra import thủ công.

---

## 14. Logging

```java
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}", request.orderId());
        // ...
        log.error("Payment failed for order: {}", orderId, exception);
    }
}
```

### Rules
- Declare logger as `private static final Logger log = LoggerFactory.getLogger(ClassName.class)`
- Import: `org.slf4j.Logger` and `org.slf4j.LoggerFactory`
- NEVER log: passwords, tokens, credit cards, personal identifiable info
- Log levels: ERROR (needs action), WARN (notable), INFO (business events), DEBUG (dev only)
- Use parameterized `{}` — NEVER string concatenation in log statements
- Controller: no logging needed (HTTP access logs cover it)
- Service: log important business events

---

## 15. Code Size Limits

| Metric | Limit | Action if exceeded |
|---|---|---|
| File | < 300 lines | Split into smaller classes |
| Method | < 50 lines | Extract methods |
| Method parameters | < 5 | Group into a record/object |
| Constructor parameters | < 7 | Split responsibilities into separate services |
| Nested blocks (if/for) | < 3 levels | Use early return or extract method |

---

## 16. Commit Checklist

- [ ] No `@Autowired` field injection — constructor injection only (explicit constructor)
- [ ] No Entity returned from controller — DTO records used
- [ ] `@Valid` on every `@RequestBody`
- [ ] Custom exceptions used — no raw `RuntimeException`
- [ ] No hardcoded config — `application.yml` + env variables
- [ ] No sensitive data in JWT claims or logs
- [ ] Service layer uses Interface + Impl pattern
- [ ] All responses wrapped in `ApiResponse`
- [ ] Tests cover happy path + error cases
- [ ] File < 300 lines, method < 50 lines
- [ ] `CONTEXT.md` updated if important logic changed
- [ ] `PROJECT-STATUS.md` updated

---

## 17. Dynamic Filter — JPA Specification

> Decision & code examples: `docs/decisions/004-filter-strategy.md`

### File structure trong feature package

```
feature/{name}/
├── {Name}Controller.java
├── {Name}Service.java              # Interface
├── {Name}ServiceImpl.java
├── {Name}Repository.java           # extends JpaSpecificationExecutor<T>
├── {Name}.java                     # Entity
├── {Name}Specification.java        # PredicateSpecification builder
└── dto/
    ├── {Name}FilterRequest.java    # Record — chỉ chứa filter fields
    ├── {Name}Response.java
    └── ...
```

### Rules
- Filter request dùng **record** — chỉ chứa filter fields, pagination do `Pageable` xử lý
- Controller nhận filter trực tiếp (không cần `@ModelAttribute`) — Spring tự bind query params vào record
- Repository extends thêm `JpaSpecificationExecutor<T>`
- Spring Data JPA 4.0: dùng `PredicateSpecification<T>` (không dùng `Specification<T>` cũ)
- Service trả về `ResultPaginationDTO` (không trả `Page<T>` trực tiếp) — bọc trong `ApiResponse` như mọi endpoint khác

---

## 18. Documentation Requirements

| When | Action |
|------|--------|
| End of every coding session | Update `docs/PROJECT-STATUS.md` |
| New feature with non-obvious logic | Create `CONTEXT.md` inside feature package |
| Architecture decision | Create new file in `docs/decisions/` |
| New API endpoints | Update `docs/API_SPEC.md` |
| Schema changes | Update `docs/DATABASE.md` |
