package vn.conganh.commercial.feature.emailoutbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import vn.conganh.commercial.AbstractIntegrationTest;

@Sql(
        statements = {
                "DELETE FROM email_outbox WHERE recipient_email LIKE 'outbox-store-it-%'",
                "DELETE FROM orders WHERE order_code LIKE 'OUTBOX-STORE-IT-%'",
                "DELETE FROM users WHERE email LIKE 'outbox-store-it-%'"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EmailOutboxStoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EmailOutboxStore store;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void claimRetryAndSend_requireTheCurrentClaimToken() {
        Instant firstAttemptAt = Instant.parse("2026-07-16T10:00:00Z");
        Long outboxId = insertOutbox(insertOrder(), firstAttemptAt.minusSeconds(1));

        EmailOutboxStore.Claim firstClaim = store
                .claimBatch(firstAttemptAt, 10, Duration.ofMinutes(2))
                .getFirst();

        assertThat(firstClaim.id()).isEqualTo(outboxId);
        assertThat(firstClaim.attemptCount()).isEqualTo(1);
        assertThat(firstClaim.recipientEmail()).isEqualTo("outbox-store-it-recipient@example.com");
        assertThat(firstClaim.templateKey()).isEqualTo(EmailTemplateKey.ORDER_COMPLETED);
        assertThat(store.claimBatch(firstAttemptAt.plusSeconds(30), 10, Duration.ofMinutes(2))).isEmpty();
        assertThat(store.markSent(
                        outboxId,
                        UUID.randomUUID(),
                        "stale-provider-id",
                        firstAttemptAt.plusSeconds(31)))
                .isFalse();

        Instant retryAt = firstAttemptAt.plus(Duration.ofMinutes(5));
        assertThat(store.markFailure(
                        outboxId,
                        firstClaim.claimToken(),
                        false,
                        retryAt,
                        "temporary failure",
                        firstAttemptAt.plusSeconds(32)))
                .isTrue();
        assertThat(store.claimBatch(retryAt.minusMillis(1), 10, Duration.ofMinutes(2))).isEmpty();

        EmailOutboxStore.Claim retryClaim = store
                .claimBatch(retryAt, 10, Duration.ofMinutes(2))
                .getFirst();
        assertThat(retryClaim.attemptCount()).isEqualTo(2);
        assertThat(retryClaim.claimToken()).isNotEqualTo(firstClaim.claimToken());
        assertThat(store.markSent(outboxId, retryClaim.claimToken(), "provider-message-id", retryAt.plusSeconds(1)))
                .isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT status, attempt_count, provider_message_id, sent_at,
                       claim_token, lease_expires_at, last_error
                FROM email_outbox
                WHERE id = ?
                """,
                outboxId);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("attempt_count")).isEqualTo(2);
        assertThat(row.get("provider_message_id")).isEqualTo("provider-message-id");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("claim_token")).isNull();
        assertThat(row.get("lease_expires_at")).isNull();
        assertThat(row.get("last_error")).isNull();
    }

    @Test
    void expiredLease_canBeReclaimedAndFencesOutTheOldWorker() {
        Instant firstAttemptAt = Instant.parse("2026-07-16T11:00:00Z");
        Long outboxId = insertOutbox(insertOrder(), firstAttemptAt.minusSeconds(1));

        EmailOutboxStore.Claim expiredClaim = store
                .claimBatch(firstAttemptAt, 1, Duration.ofSeconds(30))
                .getFirst();
        Instant afterLease = firstAttemptAt.plusSeconds(31);
        EmailOutboxStore.Claim reclaimed = store
                .claimBatch(afterLease, 1, Duration.ofSeconds(30))
                .getFirst();

        assertThat(reclaimed.id()).isEqualTo(outboxId);
        assertThat(reclaimed.attemptCount()).isEqualTo(2);
        assertThat(reclaimed.claimToken()).isNotEqualTo(expiredClaim.claimToken());
        assertThat(store.markFailure(
                        outboxId,
                        expiredClaim.claimToken(),
                        true,
                        afterLease,
                        "stale worker",
                        afterLease))
                .isFalse();
        assertThat(store.markFailure(
                        outboxId,
                        reclaimed.claimToken(),
                        true,
                        afterLease,
                        "permanent failure",
                        afterLease))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM email_outbox WHERE id = ?", String.class, outboxId))
                .isEqualTo("FAILED");
    }

    @Test
    void oneOrderCanOnlyHaveOneOrderCompletedEvent() {
        Long orderId = insertOrder();
        insertOutbox(orderId, Instant.now());

        assertThatThrownBy(() -> insertOutbox(orderId, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertOrder() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (full_name, email, password, birth_date, gender)
                VALUES ('Customer', ?, 'password', DATE '1995-01-01', 'OTHER')
                RETURNING id
                """,
                Long.class,
                "outbox-store-it-" + suffix + "@example.com");
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO orders (
                    user_id, order_code, status, subtotal, shipping_fee, discount_amount,
                    final_amount, receiver_name, receiver_phone, receiver_address,
                    payment_method, payment_status
                )
                VALUES (?, ?, 'COMPLETED', 100000, 0, 0, 100000,
                        'Customer', '0123456789', 'Ho Chi Minh City', 'COD', 'PAID')
                RETURNING id
                """,
                Long.class,
                userId,
                "OUTBOX-STORE-IT-" + suffix);
    }

    private Long insertOutbox(Long orderId, Instant nextAttemptAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO email_outbox (
                    event_type, order_id, recipient_email, template_key, status, next_attempt_at
                )
                VALUES ('ORDER_COMPLETED', ?, 'outbox-store-it-recipient@example.com',
                        'ORDER_COMPLETED', 'PENDING', ?)
                RETURNING id
                """,
                Long.class,
                orderId,
                Timestamp.from(nextAttemptAt));
    }
}
