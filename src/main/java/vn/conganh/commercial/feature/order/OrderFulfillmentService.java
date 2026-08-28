package vn.conganh.commercial.feature.order;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLog;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.CampaignReservationService;
import vn.conganh.commercial.util.constant.PaymentStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CampaignReservationService campaignReservationService;
    private final PaymentRepository paymentRepository;

    @Transactional
    public void expireOrder(Long orderId) {
        Order order = findLocked(orderId);
        Instant now = Instant.now();
        if (order.getReservationExpiresAt() == null
                || order.getReservationExpiresAt().isAfter(now)
                || order.getResourcesReleasedAt() != null
                || !"PENDING".equals(order.getStatus())
                || !"UNPAID".equals(order.getPaymentStatus())) {
            return;
        }
        releaseLockedOrder(order, "FAILED", "PAYMENT_TIMEOUT");
        paymentRepository.findWithLockByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });
    }

    /** The caller must already hold the order row lock. */
    public boolean confirmLockedOrder(Order order) {
        if (order.getResourcesReleasedAt() != null) {
            order.setPaymentStatus("REFUND_PENDING");
            orderRepository.save(order);
            paymentRepository.findWithLockByOrderId(order.getId()).ifPresent(payment -> {
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepository.save(payment);
            });
            return false;
        }
        Instant now = Instant.now();
        if (order.getReservationExpiresAt() != null
                && !order.getReservationExpiresAt().isAfter(now)) {
            releaseLockedOrder(order, "FAILED", "PAYMENT_TIMEOUT");
            return false;
        }
        campaignReservationService.confirmAllocationsForOrder(order.getId(), now);
        return true;
    }

    /** The caller must already hold the order row lock. Idempotent by resources_released_at. */
    public boolean releaseLockedOrder(Order order, String paymentStatus, String reason) {
        if (order.getResourcesReleasedAt() != null) {
            return false;
        }
        Instant now = Instant.now();
        campaignReservationService.releaseAllocationsForOrder(order.getId(), now);

        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            if (variantRepository.restoreStock(item.getVariantId(), item.getQuantity()) != 1) {
                throw new IllegalStateException("Cannot restore stock for variant " + item.getVariantId());
            }
            variantRepository.findById(item.getVariantId()).ifPresent(variant -> {
                InventoryLog inventoryLog = new InventoryLog();
                inventoryLog.setVariant(variant);
                inventoryLog.setChangeQuantity(item.getQuantity());
                inventoryLog.setType("CANCEL");
                inventoryLog.setReferenceType("ORDER_ITEM");
                inventoryLog.setReferenceId(item.getId());
                inventoryLog.setReason(reason);
                inventoryLogRepository.save(inventoryLog);
            });
        }

        couponUsageRepository.findByOrderId(order.getId()).ifPresent(usage -> {
            couponRepository.releaseUsage(usage.getCoupon().getId());
            couponUsageRepository.delete(usage);
        });

        String previousStatus = order.getStatus();
        order.setResourcesReleasedAt(now);
        order.setStatus("CANCELLED");
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);

        if (!"CANCELLED".equals(previousStatus)) {
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrder(order);
            history.setFromStatus(previousStatus);
            history.setToStatus("CANCELLED");
            history.setReason(reason);
            historyRepository.save(history);
        }
        log.info("Released order resources once for order {}, reason {}", order.getId(), reason);
        return true;
    }

    @Transactional(readOnly = true)
    public Order find(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    public Order findLocked(Long orderId) {
        return orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }
}
