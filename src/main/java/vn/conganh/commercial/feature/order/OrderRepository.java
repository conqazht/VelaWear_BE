package vn.conganh.commercial.feature.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "user")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByOrderCodeAndUserId(String orderCode, Long userId);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findWithLockById(@Param("id") Long id);

    Optional<Order> findByUserIdAndCheckoutIdempotencyKey(Long userId, String checkoutIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.user.id = :userId and o.checkoutIdempotencyKey = :checkoutIdempotencyKey
            """)
    Optional<Order> findWithLockByUserIdAndCheckoutIdempotencyKey(
            @Param("userId") Long userId,
            @Param("checkoutIdempotencyKey") String checkoutIdempotencyKey);

    @Query("""
            select o.id from Order o
            where o.status = 'PENDING'
              and o.paymentStatus = 'UNPAID'
              and o.resourcesReleasedAt is null
              and o.reservationExpiresAt is not null
              and o.reservationExpiresAt <= :now
            order by o.reservationExpiresAt asc
            """)
    List<Long> findExpiredReservationIds(@Param("now") Instant now, Pageable pageable);

    boolean existsByOrderCode(String orderCode);

    Page<Order> findByUserId(Long userId, Pageable pageable);
}
