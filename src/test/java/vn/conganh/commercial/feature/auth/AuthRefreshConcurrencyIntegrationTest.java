package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.exception.RefreshTokenSessionNotFoundException;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSession;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@DisplayName("Refresh token concurrency integration")
class AuthRefreshConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:active:";
    private static final String RACE_USER_EMAIL = "refresh.race@velawear.local";
    private static final long RACE_TIMEOUT_SECONDS = 10;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 2;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private RefreshTokenSessionService refreshTokenSessionService;

    @BeforeEach
    void resetFixture() {
        deleteRaceUser();
        flushRedis();
    }

    @AfterEach
    void cleanUpFixture() {
        deleteRaceUser();
        flushRedis();
    }

    @Test
    @DisplayName("Hai CAS Redis đồng thời chỉ tạo đúng một refresh session kế nhiệm")
    void rotateIfCurrent_concurrentReplacements_onlyOneWins() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(30);
        Instant expiresAt = Instant.now().plusSeconds(300);
        RefreshTokenSession current = session("old-jti", "old-hash", issuedAt, expiresAt);
        RefreshTokenSession firstReplacement = session("new-jti-a", "new-hash-a", issuedAt, expiresAt);
        RefreshTokenSession secondReplacement = session("new-jti-b", "new-hash-b", issuedAt, expiresAt);
        refreshTokenSessionService.create(current);

        List<Boolean> results = raceValues(
                () -> refreshTokenSessionService.rotateIfCurrent(current, firstReplacement),
                () -> refreshTokenSessionService.rotateIfCurrent(current, secondReplacement));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(refreshTokenSessionService.find(current.jti())).isEmpty();
        Optional<RefreshTokenSession> firstStored = refreshTokenSessionService.find(firstReplacement.jti());
        Optional<RefreshTokenSession> secondStored = refreshTokenSessionService.find(secondReplacement.jti());
        assertThat(firstStored.isPresent() ^ secondStored.isPresent()).isTrue();
        Set<String> activeKeys = redisTemplate.keys(REFRESH_KEY_PREFIX + "*");
        assertThat(activeKeys).hasSize(1);
        String winnerJti = firstStored.isPresent() ? firstReplacement.jti() : secondReplacement.jti();
        Long ttlSeconds = redisTemplate.getExpire(REFRESH_KEY_PREFIX + winnerJti, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isPositive();
    }

    @Test
    @DisplayName("CAS Redis không xóa session cũ khi JTI kế nhiệm đã tồn tại")
    void rotateIfCurrent_existingReplacement_keepsCurrentSession() {
        Instant issuedAt = Instant.now().minusSeconds(30);
        Instant expiresAt = Instant.now().plusSeconds(300);
        RefreshTokenSession current = session("old-jti", "old-hash", issuedAt, expiresAt);
        RefreshTokenSession replacement = session("new-jti", "new-hash", issuedAt, expiresAt);
        refreshTokenSessionService.create(current);
        refreshTokenSessionService.create(replacement);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> refreshTokenSessionService.rotateIfCurrent(current, replacement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Replacement refresh session already exists");
        assertThat(refreshTokenSessionService.find(current.jti())).contains(current);
        assertThat(refreshTokenSessionService.find(replacement.jti())).contains(replacement);
    }

    @Test
    @DisplayName("Hai request refresh cùng token chỉ một request nhận token kế nhiệm")
    void refreshToken_concurrentRequests_onlyOneSucceeds() throws Exception {
        String password = "Password123!";
        User raceUser = userRepository.save(user(RACE_USER_EMAIL, password));
        TokenResponse login = authService.authenticate(
                new LoginRequest(RACE_USER_EMAIL, password), "Chrome", "127.0.0.1");
        String oldRefreshToken = login.refreshToken();
        String oldJti = jwtId(oldRefreshToken);
        CountDownLatch bothRequestsReadCurrentSession = new CountDownLatch(2);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<RefreshTokenSession> found = (Optional<RefreshTokenSession>) invocation.callRealMethod();
            if (found.isPresent()) {
                bothRequestsReadCurrentSession.countDown();
                boolean bothReady = bothRequestsReadCurrentSession.await(5, TimeUnit.SECONDS);
                if (!bothReady) {
                    throw new IllegalStateException("Both refresh requests did not read the current session");
                }
            }
            return found;
        }).when(refreshTokenSessionService).find(oldJti);

        List<RefreshAttempt> attempts = raceValues(
                () -> refresh(oldRefreshToken),
                () -> refresh(oldRefreshToken));

        List<RefreshAttempt> successes = attempts.stream()
                .filter(attempt -> attempt.response() != null)
                .toList();
        List<Throwable> failures = attempts.stream()
                .map(RefreshAttempt::failure)
                .filter(failure -> failure != null)
                .toList();
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(RefreshTokenSessionNotFoundException.class)
                .hasMessage("Refresh session is expired or revoked");

        String winningRefreshToken = successes.getFirst().response().refreshToken();
        String winningJti = jwtId(winningRefreshToken);
        assertThat(refreshTokenSessionService.find(oldJti)).isEmpty();
        assertThat(refreshTokenSessionService.find(winningJti)).isPresent();
        assertThat(redisTemplate.keys(REFRESH_KEY_PREFIX + "*")).hasSize(1);

        Long auditRows = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ?",
                Long.class,
                raceUser.getId());
        Long revokedRows = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked = true",
                Long.class,
                raceUser.getId());
        Long activeRows = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked = false",
                Long.class,
                raceUser.getId());
        assertThat(auditRows).isEqualTo(2L);
        assertThat(revokedRows).isEqualTo(1L);
        assertThat(activeRows).isEqualTo(1L);
    }

    private void deleteRaceUser() {
        jdbcTemplate.update(
                "delete from refresh_tokens where user_id in (select id from users where email = ?)",
                RACE_USER_EMAIL);
        jdbcTemplate.update("delete from users where email = ?", RACE_USER_EMAIL);
    }

    private void flushRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    private RefreshAttempt refresh(String refreshToken) {
        try {
            TokenResponse response = authService.refreshToken(new RefreshTokenRequest(refreshToken));
            return new RefreshAttempt(response, null);
        } catch (RuntimeException exception) {
            return new RefreshAttempt(null, exception);
        }
    }

    private RefreshTokenSession session(String jti, String tokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshTokenSession(
                jti, 1L, tokenHash, "Chrome", "127.0.0.1", issuedAt, expiresAt);
    }

    private User user(String email, String rawPassword) {
        User user = new User();
        user.setFullName("Refresh Race User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }

    private String jwtId(String rawJwt) throws Exception {
        return SignedJWT.parse(rawJwt).getJWTClaimsSet().getJWTID();
    }

    private <T> List<T> raceValues(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(
                2,
                Thread.ofPlatform().daemon().name("refresh-race-", 0).factory());
        Future<T> firstFuture = null;
        Future<T> secondFuture = null;
        try {
            firstFuture = executor.submit(() -> runAfterStart(ready, start, first));
            secondFuture = executor.submit(() -> runAfterStart(ready, start, second));
            if (!ready.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test workers did not become ready");
            }
            start.countDown();
            return List.of(
                    firstFuture.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    secondFuture.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            cancelIfRunning(firstFuture);
            cancelIfRunning(secondFuture);
            executor.shutdownNow();
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Refresh race workers did not stop within the timeout");
            }
        }
    }

    private void cancelIfRunning(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private <T> T runAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<T> task) throws Exception {
        ready.countDown();
        start.await();
        return task.call();
    }

    private record RefreshAttempt(TokenResponse response, Throwable failure) {
    }
}
