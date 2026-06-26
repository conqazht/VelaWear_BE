package vn.conganh.commercial.feature.order;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryResponse;

public interface OrderService {

    ResultPaginationDTO getAllOrders(Pageable pageable);

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderByOrderCode(String orderCode);

    ResultPaginationDTO getOrdersByUserId(Long userId, Pageable pageable);

    ResultPaginationDTO getOrderStatusHistories(Long id, Pageable pageable);

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse updateOrder(Long id, UpdateOrderRequest request);

    void deleteOrder(Long id);
}
