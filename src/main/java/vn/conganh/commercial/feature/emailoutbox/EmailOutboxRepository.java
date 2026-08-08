package vn.conganh.commercial.feature.emailoutbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    boolean existsByEventTypeAndOrder_Id(EmailEventType eventType, Long orderId);
}
