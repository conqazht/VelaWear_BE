package vn.conganh.commercial.feature.emailoutbox;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmailOutboxStore {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT id
                FROM email_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                   OR (status = 'PROCESSING' AND lease_expires_at <= :now)
                ORDER BY COALESCE(lease_expires_at, next_attempt_at), id
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE email_outbox AS outbox
            SET status = 'PROCESSING',
                claim_token = :claimToken,
                lease_expires_at = :leaseExpiresAt,
                attempt_count = outbox.attempt_count + 1,
                updated_at = :now
            FROM candidates
            WHERE outbox.id = candidates.id
            RETURNING outbox.id,
                      outbox.order_id,
                      outbox.recipient_email,
                      outbox.template_key,
                      outbox.attempt_count,
                      outbox.created_at,
                      outbox.claim_token
            """;

    private static final String MARK_SENT_SQL = """
            UPDATE email_outbox
            SET status = 'SENT',
                provider_message_id = :providerMessageId,
                sent_at = :now,
                claim_token = NULL,
                lease_expires_at = NULL,
                last_error = NULL,
                updated_at = :now
            WHERE id = :id
              AND status = 'PROCESSING'
              AND claim_token = :claimToken
            """;

    private static final String MARK_FAILURE_SQL = """
            UPDATE email_outbox
            SET status = :status,
                next_attempt_at = :nextAttemptAt,
                claim_token = NULL,
                lease_expires_at = NULL,
                last_error = :lastError,
                updated_at = :now
            WHERE id = :id
              AND status = 'PROCESSING'
              AND claim_token = :claimToken
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EmailOutboxStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<Claim> claimBatch(Instant now, int batchSize, Duration leaseDuration) {
        UUID claimToken = UUID.randomUUID();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("batchSize", batchSize)
                .addValue("claimToken", claimToken)
                .addValue("leaseExpiresAt", Timestamp.from(now.plus(leaseDuration)));

        return jdbcTemplate.query(CLAIM_SQL, parameters, (resultSet, rowNumber) -> new Claim(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("recipient_email"),
                EmailTemplateKey.valueOf(resultSet.getString("template_key")),
                resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getObject("claim_token", UUID.class)));
    }

    @Transactional
    public boolean markSent(Long id, UUID claimToken, String providerMessageId, Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimToken", claimToken)
                .addValue("providerMessageId", providerMessageId)
                .addValue("now", Timestamp.from(now));
        return jdbcTemplate.update(MARK_SENT_SQL, parameters) == 1;
    }

    @Transactional
    public boolean markFailure(
            Long id,
            UUID claimToken,
            boolean terminal,
            Instant nextAttemptAt,
            String lastError,
            Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimToken", claimToken)
                .addValue("status", terminal ? EmailOutboxStatus.FAILED.name() : EmailOutboxStatus.PENDING.name())
                .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .addValue("lastError", lastError)
                .addValue("now", Timestamp.from(now));
        return jdbcTemplate.update(MARK_FAILURE_SQL, parameters) == 1;
    }

    public record Claim(
            Long id,
            Long orderId,
            String recipientEmail,
            EmailTemplateKey templateKey,
            int attemptCount,
            Instant createdAt,
            UUID claimToken
    ) {
    }
}
