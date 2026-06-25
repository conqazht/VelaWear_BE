package vn.conganh.commercial.feature.order;

import java.util.List;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryResponse;

public interface OrderService {

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderByOrderCode(String orderCode);

    List<OrderResponse> getOrdersByUserId(Long userId);

    List<OrderStatusHistoryResponse> getOrderStatusHistories(Long id);

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse updateOrder(Long id, UpdateOrderRequest request);

    void deleteOrder(Long id);
}
