package vn.conganh.commercial.feature.order;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderFilterRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryFilterRequest;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.FilterSpecifications;
import vn.conganh.commercial.feature.checkout.OrderResourceLifecycleService;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.emailoutbox.OrderCompletedEmailOutboxService;
import vn.conganh.commercial.util.constant.PaymentStatus;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderResourceLifecycleService resourceLifecycleService;
    private final PaymentRepository paymentRepository;
    private final OrderCompletedEmailOutboxService orderCompletedEmailOutboxService;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllOrders(OrderFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(orderRepository.findAll(Specification.where(OrderSpecification.build(filter)), pageable)
                .map(OrderResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return toDetailedResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", orderCode));
        return toDetailedResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyOrders(String email, Pageable pageable) {
        User user = findActiveUser(email);
        return ResultPaginationDTO.fromPage(orderRepository.findByUserId(user.getId(), pageable)
                .map(OrderResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(String email, Long id) {
        User user = findActiveUser(email);
        Order order = orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return toDetailedResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderByOrderCode(String email, String orderCode) {
        User user = findActiveUser(email);
        Order order = orderRepository.findByOrderCodeAndUserId(orderCode, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", orderCode));
        return toDetailedResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyOrderStatusHistories(
            String email,
            Long id,
            OrderStatusHistoryFilterRequest filter,
            Pageable pageable) {
        User user = findActiveUser(email);
        orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return ResultPaginationDTO.fromPage(orderStatusHistoryRepository
                .findAll(Specification.where(OrderStatusHistorySpecification.build(id, filter)), pageable)
                .map(OrderStatusHistoryResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getOrdersByUserId(Long userId, OrderFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("userId", userId, filter == null ? null : filter.userId());
        OrderFilterRequest scopedFilter = filter == null
                ? new OrderFilterRequest(
                        userId, null, null, null, null, null, null, null, null, null, null, null, null)
                : filter.withUserId(userId);

        return ResultPaginationDTO.fromPage(orderRepository.findAll(Specification.where(OrderSpecification.build(scopedFilter)), pageable)
                .map(OrderResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getOrderStatusHistories(
            Long id,
            OrderStatusHistoryFilterRequest filter,
            Pageable pageable) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", "id", id);
        }

        return ResultPaginationDTO.fromPage(orderStatusHistoryRepository.findAll(Specification.where(OrderStatusHistorySpecification.build(id, filter)), pageable)
                .map(OrderStatusHistoryResponse::fromEntity));
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (orderRepository.existsByOrderCode(request.orderCode())) {
            throw new InvalidRequestException("Order code already exists");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(request.orderCode());
        order.setStatus(request.status() != null ? request.status() : "PENDING");
        order.setSubtotal(request.subtotal());
        order.setShippingFee(request.shippingFee() != null ? request.shippingFee() : BigDecimal.ZERO);
        order.setDiscountAmount(request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO);
        order.setFinalAmount(request.finalAmount());
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus(request.paymentStatus() != null ? request.paymentStatus() : "UNPAID");

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        Order order = orderRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String previousStatus = order.getStatus();
        assertCheckoutManagedFieldsAreImmutable(order, request);
        assertCompletedEmailFieldsAreImmutable(order, request);
        if ("CANCELLED".equals(request.status()) && !"CANCELLED".equals(previousStatus)) {
            if (!java.util.Set.of("PENDING", "CONFIRMED").contains(previousStatus)) {
                throw new InvalidRequestException(
                        "Only PENDING or CONFIRMED orders can be cancelled; use return/refund after shipping");
            }
            if ("PAID".equals(order.getPaymentStatus())) {
                throw new InvalidRequestException("A paid order must use the refund workflow, not cancellation");
            }
            resourceLifecycleService.releaseLockedOrder(order, "FAILED", "ADMIN_CANCELLED");
            paymentRepository.findWithLockByOrderId(id).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(payment);
                }
            });
            return toDetailedResponse(order);
        }
        if (request.status() != null) {
            order.setStatus(request.status());
        }
        if (request.shippingFee() != null) {
            order.setShippingFee(request.shippingFee());
        }
        if (request.discountAmount() != null) {
            order.setDiscountAmount(request.discountAmount());
        }
        if (request.finalAmount() != null) {
            order.setFinalAmount(request.finalAmount());
        }
        if (request.receiverName() != null) {
            order.setReceiverName(request.receiverName());
        }
        if (request.receiverPhone() != null) {
            order.setReceiverPhone(request.receiverPhone());
        }
        if (request.receiverAddress() != null) {
            order.setReceiverAddress(request.receiverAddress());
        }
        if (request.paymentMethod() != null) {
            order.setPaymentMethod(request.paymentMethod());
        }
        if (request.paymentStatus() != null) {
            order.setPaymentStatus(request.paymentStatus());
        }

        Order savedOrder = orderRepository.save(order);
        recordStatusHistory(savedOrder, previousStatus, request.status());
        if (!"COMPLETED".equals(previousStatus) && "COMPLETED".equals(savedOrder.getStatus())) {
            orderCompletedEmailOutboxService.enqueue(savedOrder);
        }
        return toDetailedResponse(savedOrder);
    }

    private void assertCompletedEmailFieldsAreImmutable(Order order, UpdateOrderRequest request) {
        if (!"COMPLETED".equals(order.getStatus())) {
            return;
        }
        boolean changesRenderedEmailData = (request.finalAmount() != null
                        && request.finalAmount().compareTo(order.getFinalAmount()) != 0)
                || (request.receiverName() != null
                        && !Objects.equals(request.receiverName(), order.getReceiverName()))
                || (request.receiverAddress() != null
                        && !Objects.equals(request.receiverAddress(), order.getReceiverAddress()))
                || (request.paymentMethod() != null
                        && !Objects.equals(request.paymentMethod(), order.getPaymentMethod()));
        if (changesRenderedEmailData) {
            throw new InvalidRequestException(
                    "Completed order receipt fields cannot be changed after the delivery email is queued");
        }
    }

    private void assertCheckoutManagedFieldsAreImmutable(Order order, UpdateOrderRequest request) {
        if (order.getCheckoutIdempotencyKey() == null) {
            return;
        }
        boolean validCodCollection = "COD".equals(order.getPaymentMethod())
                && "UNPAID".equals(order.getPaymentStatus())
                && "PAID".equals(request.paymentStatus());
        if (request.shippingFee() != null
                || request.discountAmount() != null
                || request.finalAmount() != null
                || request.paymentMethod() != null
                || (request.paymentStatus() != null && !validCodCollection)) {
            throw new InvalidRequestException(
                    "Checkout totals and payment fields are managed by checkout/payment lifecycle services");
        }
        if (request.status() != null
                && order.getResourcesReleasedAt() != null
                && !request.status().equals(order.getStatus())) {
            throw new InvalidRequestException("A released checkout order cannot be reopened");
        }
        if (request.status() != null
                && order.getReservationExpiresAt() != null
                && "UNPAID".equals(order.getPaymentStatus())
                && !"PENDING".equals(request.status())
                && !"CANCELLED".equals(request.status())) {
            throw new InvalidRequestException(
                    "An unpaid online checkout must remain PENDING or be cancelled through its lifecycle");
        }
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        if (!orderItemRepository.findByOrderId(id).isEmpty()) {
            throw new InvalidRequestException("An order with items cannot be deleted; cancel or refund it instead");
        }
        orderRepository.delete(order);
    }

    private void recordStatusHistory(Order order, String previousStatus, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.equals(previousStatus)) {
            return;
        }

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(previousStatus);
        history.setToStatus(requestedStatus);
        orderStatusHistoryRepository.save(history);
    }

    private OrderResponse toDetailedResponse(Order order) {
        return OrderResponse.fromEntity(order, orderItemRepository.findByOrderId(order.getId()));
    }

    private User findActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
