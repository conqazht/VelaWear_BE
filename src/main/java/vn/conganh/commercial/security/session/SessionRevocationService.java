package vn.conganh.commercial.security.session;

import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
public class SessionRevocationService {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public SessionRevocationService(
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            EntityManager entityManager,
            ApplicationEventPublisher eventPublisher) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * PostgreSQL securityVersion is the source of truth. Redis session cleanup
     * is dispatched only after this transaction commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long revokeAll(User user, SessionRevocationReason reason) {
        if (userRepository.incrementSecurityVersion(user.getId()) != 1) {
            throw new IllegalStateException("Unable to increment user security version");
        }
        // The bulk update is atomic but bypasses the managed entity. Refresh it
        // after the query has flushed pending password/email changes.
        entityManager.refresh(user);
        long nextVersion = user.getSecurityVersion();
        refreshTokenService.revokeAllByUserId(user.getId());
        eventPublisher.publishEvent(new SessionsRevokedEvent(user.getId(), nextVersion, reason));
        return nextVersion;
    }
}
