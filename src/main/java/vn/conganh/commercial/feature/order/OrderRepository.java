package vn.conganh.commercial.feature.order;

import java.math.BigDecimal;
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
import vn.conganh.commercial.feature.dashboard.dto.TopSpendingCustomerProjection;

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

    @Query("""
            select coalesce(sum(o.finalAmount), 0)
            from Order o
            where o.user.id = :userId
              and o.status = 'COMPLETED'
              and o.createdAt >= :since
            """)
    BigDecimal findCompletedSpendSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("""
            select coalesce(sum(o.finalAmount), 0)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.status not in ('CANCELLED')
            """)
    BigDecimal sumRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(o)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
            """)
    long countOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(o)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.status = 'CANCELLED'
            """)
    long countCancelledOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select o
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.status not in ('CANCELLED')
            order by o.createdAt asc
            """)
    List<Order> findRevenueOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @EntityGraph(attributePaths = "user")
    @Query("select o from Order o order by o.createdAt desc")
    List<Order> findRecentOrders(Pageable pageable);

    @Query("""
            select coalesce(sum(o.finalAmount), 0)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.paymentStatus = 'PAID'
            """)
    BigDecimal sumPaidRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select coalesce(sum(o.finalAmount), 0)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.paymentStatus = 'UNPAID'
              and o.status not in ('CANCELLED')
            """)
    BigDecimal sumPendingRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select coalesce(sum(o.finalAmount), 0)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and (o.paymentStatus = 'REFUNDED' or o.status = 'REFUNDED')
            """)
    BigDecimal sumRefundedRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select coalesce(sum(o.discountAmount), 0)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.status not in ('CANCELLED')
            """)
    BigDecimal sumDiscountAmountBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(o)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.paymentStatus = 'PAID'
            """)
    long countPaidOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(o)
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
              and o.paymentStatus = 'UNPAID'
              and o.status not in ('CANCELLED')
            """)
    long countPendingOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select o
            from Order o
            where o.createdAt >= :start
              and o.createdAt < :end
            order by o.createdAt asc
            """)
    List<Order> findAllOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select count(distinct o.user.id)
            from Order o
            where o.status not in ('CANCELLED')
            """)
    long countDistinctActiveBuyers();

    @Query("""
            select o.user.id
            from Order o
            where o.status not in ('CANCELLED')
            group by o.user.id
            having count(o) >= 2
            """)
    List<Long> findRepeatCustomerIds();

    @Query("""
            select o.user.id as userId,
                   o.user.fullName as fullName,
                   o.user.email as email,
                   max(o.receiverPhone) as receiverPhone,
                   count(o) as totalOrders,
                   sum(o.finalAmount) as totalSpent,
                   max(o.createdAt) as lastOrderDate
            from Order o
            where o.status not in ('CANCELLED')
            group by o.user.id, o.user.fullName, o.user.email
            order by sum(o.finalAmount) desc
            """)
    List<TopSpendingCustomerProjection> findTopSpendingCustomers(Pageable pageable);
}
