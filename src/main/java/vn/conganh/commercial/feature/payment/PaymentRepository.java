package vn.conganh.commercial.feature.payment;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    @EntityGraph(attributePaths = "order")
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByOrderId(Long orderId);
}

