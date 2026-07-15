package vn.conganh.commercial.feature.checkout;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderStatusHistory;
import vn.conganh.commercial.feature.order.OrderStatusHistoryRepository;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLog;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.SaleAllocation;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCustomerUsageRepository;
import vn.conganh.commercial.util.constant.PaymentStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderResourceLifecycleService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final SaleAllocationRepository allocationRepository;
    private final SaleCampaignItemRepository campaignItemRepository;
    private final SaleCustomerUsageRepository usageRepository;
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
        for (SaleAllocation allocation : allocationRepository.findWithLockByOrderId(order.getId())) {
            if (allocation.getStatus() != SaleAllocationStatus.RESERVED) {
                continue;
            }
            int quantity = allocation.getQuantity();
            Long itemId = allocation.getCampaignItem().getId();
            Long userId = allocation.getUser().getId();
            if (campaignItemRepository.confirmQuota(itemId, quantity) != 1
                    || usageRepository.confirm(itemId, userId, quantity) != 1) {
                throw new IllegalStateException("Cannot confirm flash allocation " + allocation.getId());
            }
            allocation.setStatus(SaleAllocationStatus.CONFIRMED);
            allocation.setConfirmedAt(now);
        }
        allocationRepository.flush();
        return true;
    }

    /** The caller must already hold the order row lock. Idempotent by resources_released_at. */
    public boolean releaseLockedOrder(Order order, String paymentStatus, String reason) {
        if (order.getResourcesReleasedAt() != null) {
            return false;
        }
        Instant now = Instant.now();
        List<SaleAllocation> allocations = allocationRepository.findWithLockByOrderId(order.getId());
        for (SaleAllocation allocation : allocations) {
            int quantity = allocation.getQuantity();
            Long itemId = allocation.getCampaignItem().getId();
            Long userId = allocation.getUser().getId();
            if (allocation.getStatus() == SaleAllocationStatus.RESERVED) {
                if (campaignItemRepository.releaseQuota(itemId, quantity) != 1
                        || usageRepository.release(itemId, userId, quantity) != 1) {
                    throw new IllegalStateException("Cannot release flash allocation " + allocation.getId());
                }
                allocation.setStatus(SaleAllocationStatus.RELEASED);
                allocation.setReleasedAt(now);
            } else if (allocation.getStatus() == SaleAllocationStatus.CONFIRMED) {
                if (campaignItemRepository.reverseSoldQuota(itemId, quantity) != 1
                        || usageRepository.reverse(itemId, userId, quantity) != 1) {
                    throw new IllegalStateException("Cannot reverse flash allocation " + allocation.getId());
                }
                allocation.setStatus(SaleAllocationStatus.REVERSED);
                allocation.setReversedAt(now);
            }
        }
        allocationRepository.saveAll(allocations);

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
