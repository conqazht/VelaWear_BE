package vn.conganh.commercial.feature.order;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderFilterRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryFilterRequest;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;

public interface OrderService {

    ResultPaginationDTO getAllOrders(OrderFilterRequest filter, Pageable pageable);

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderByOrderCode(String orderCode);

    ResultPaginationDTO getOrdersByUserId(Long userId, OrderFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getOrderStatusHistories(Long id, OrderStatusHistoryFilterRequest filter, Pageable pageable);

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse updateOrder(Long id, UpdateOrderRequest request);

    void deleteOrder(Long id);
}
