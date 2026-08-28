package vn.conganh.commercial.feature.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsage;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLog;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.CampaignReservationService;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Order - OrderFulfillmentService")
class OrderFulfillmentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryLogRepository inventoryLogRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository couponUsageRepository;
    @Mock private CampaignReservationService campaignReservationService;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderFulfillmentService fulfillmentService;

    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);

        order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setUser(user);
        order.setStatus("PENDING");
        order.setPaymentStatus("UNPAID");
    }

    @Nested
    @DisplayName("find & findLocked")
    class FindTests {
        @Test
        void find_returnsOrderWhenFound() {
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            assertThat(fulfillmentService.find(100L)).isEqualTo(order);
        }

        @Test
        void find_throwsWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> fulfillmentService.find(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void findLocked_returnsOrderWhenFound() {
            when(orderRepository.findWithLockById(100L)).thenReturn(Optional.of(order));
            assertThat(fulfillmentService.findLocked(100L)).isEqualTo(order);
        }
    }

    @Nested
    @DisplayName("confirmLockedOrder")
    class ConfirmTests {
        @Test
        void confirmLockedOrder_setsRefundPendingWhenAlreadyReleased() {
            order.setResourcesReleasedAt(Instant.now());
            Payment payment = new Payment();
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findWithLockByOrderId(100L)).thenReturn(Optional.of(payment));

            boolean confirmed = fulfillmentService.confirmLockedOrder(order);

            assertThat(confirmed).isFalse();
            assertThat(order.getPaymentStatus()).isEqualTo("REFUND_PENDING");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
            verify(orderRepository).save(order);
            verify(paymentRepository).save(payment);
        }

        @Test
        void confirmLockedOrder_confirmsFlashAllocationsSuccessfully() {
            boolean confirmed = fulfillmentService.confirmLockedOrder(order);

            assertThat(confirmed).isTrue();
            verify(campaignReservationService).confirmAllocationsForOrder(eq(100L), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("releaseLockedOrder")
    class ReleaseTests {
        @Test
        void releaseLockedOrder_returnsFalseIfAlreadyReleased() {
            order.setResourcesReleasedAt(Instant.now());

            boolean released = fulfillmentService.releaseLockedOrder(order, "FAILED", "TEST");

            assertThat(released).isFalse();
            verify(campaignReservationService, never()).releaseAllocationsForOrder(anyLong(), any());
        }

        @Test
        void releaseLockedOrder_releasesAllocationsStockAndCoupon() {
            // Stock & Inventory
            OrderItem orderItem = new OrderItem();
            ReflectionTestUtils.setField(orderItem, "id", 200L);
            orderItem.setVariantId(500L);
            orderItem.setQuantity(2);

            ProductVariant variant = new ProductVariant();
            ReflectionTestUtils.setField(variant, "id", 500L);

            when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(orderItem));
            when(variantRepository.restoreStock(500L, 2)).thenReturn(1);
            when(variantRepository.findById(500L)).thenReturn(Optional.of(variant));

            // Coupon
            Coupon coupon = new Coupon();
            ReflectionTestUtils.setField(coupon, "id", 30L);
            CouponUsage usage = new CouponUsage();
            usage.setCoupon(coupon);
            when(couponUsageRepository.findByOrderId(100L)).thenReturn(Optional.of(usage));

            boolean released = fulfillmentService.releaseLockedOrder(order, "FAILED", "CUSTOMER_CANCELLED");

            assertThat(released).isTrue();
            assertThat(order.getResourcesReleasedAt()).isNotNull();
            assertThat(order.getStatus()).isEqualTo("CANCELLED");
            assertThat(order.getPaymentStatus()).isEqualTo("FAILED");

            verify(campaignReservationService).releaseAllocationsForOrder(eq(100L), any(Instant.class));
            verify(variantRepository).restoreStock(500L, 2);
            verify(inventoryLogRepository).save(any(InventoryLog.class));
            verify(couponRepository).releaseUsage(30L);
            verify(couponUsageRepository).delete(usage);
            verify(orderRepository).save(order);
            verify(historyRepository).save(any(OrderStatusHistory.class));
        }
    }

    @Nested
    @DisplayName("expireOrder")
    class ExpireTests {
        @Test
        void expireOrder_skipsIfReservationNotExpired() {
            order.setReservationExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
            when(orderRepository.findWithLockById(100L)).thenReturn(Optional.of(order));

            fulfillmentService.expireOrder(100L);

            verify(orderRepository, never()).save(order);
        }

        @Test
        void expireOrder_releasesAndFailsPendingPaymentWhenExpired() {
            order.setReservationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            when(orderRepository.findWithLockById(100L)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of());
            when(couponUsageRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            Payment payment = new Payment();
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findWithLockByOrderId(100L)).thenReturn(Optional.of(payment));

            fulfillmentService.expireOrder(100L);

            assertThat(order.getStatus()).isEqualTo("CANCELLED");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentRepository).save(payment);
        }
    }
}
