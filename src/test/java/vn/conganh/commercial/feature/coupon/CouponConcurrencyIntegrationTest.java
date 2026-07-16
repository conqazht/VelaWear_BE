package vn.conganh.commercial.feature.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

@DisplayName("Coupon counter concurrency")
class CouponConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String CODE = "BE003-LOCK";

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        tx().executeWithoutResult(status -> couponRepository.findByCode(CODE).ifPresent(couponRepository::delete));
    }

    @Test
    @DisplayName("admin locked update serializes with atomic coupon consume")
    void adminLockedUpdate_serializesWithAtomicConsume() throws Exception {
        Long couponId = tx().execute(status -> couponRepository.saveAndFlush(activeCoupon()).getId());
        CountDownLatch adminHasLock = new CountDownLatch(1);
        CountDownLatch consumeStarted = new CountDownLatch(1);
        CountDownLatch allowAdminCommit = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Void> adminMutation = executorService.submit(() -> {
                tx().executeWithoutResult(status -> {
                    Coupon lockedCoupon = couponRepository.findWithLockById(couponId).orElseThrow();
                    adminHasLock.countDown();
                    await(allowAdminCommit);

                    lockedCoupon.setUsageLimit(3);
                    lockedCoupon.setMaxDiscount(BigDecimal.valueOf(20000));
                    couponRepository.saveAndFlush(lockedCoupon);
                });
                return null;
            });

            await(adminHasLock);

            Future<Integer> atomicConsume = executorService.submit(() -> tx().execute(status -> {
                consumeStarted.countDown();
                return couponRepository.consumeUsage(couponId, Instant.now());
            }));

            await(consumeStarted);
            assertThatThrownBy(() -> atomicConsume.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowAdminCommit.countDown();

            assertThat(adminMutation.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(atomicConsume.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }

        Coupon reloaded = couponRepository.findById(couponId).orElseThrow();
        assertThat(reloaded.getUsedCount()).isEqualTo(1);
        assertThat(reloaded.getUsageLimit()).isEqualTo(3);
        assertThat(reloaded.getMaxDiscount()).isEqualByComparingTo("20000");
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private Coupon activeCoupon() {
        Instant now = Instant.now();
        Coupon coupon = new Coupon();
        coupon.setCode(CODE);
        coupon.setType(CouponType.PERCENTAGE);
        coupon.setValue(BigDecimal.TEN);
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setMaxDiscount(BigDecimal.valueOf(10000));
        coupon.setUsageLimit(2);
        coupon.setStartDate(now.minusSeconds(60));
        coupon.setEndDate(now.plusSeconds(3600));
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
