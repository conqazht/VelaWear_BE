package vn.conganh.commercial.feature.order;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
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

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

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
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderCode", orderCode));
        return OrderResponse.fromEntity(order);
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
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String previousStatus = order.getStatus();
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
        return OrderResponse.fromEntity(savedOrder);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
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
}
