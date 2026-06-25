package vn.conganh.commercial.feature.order;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryResponse;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping(version = "1")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrders() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders()));
    }

    @GetMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @GetMapping(path = "/code/{orderCode}", version = "1")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderByOrderCode(orderCode)));
    }

    @GetMapping(path = "/user/{userId}", version = "1")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByUserId(userId)));
    }

    @GetMapping(path = "/{id}/status-histories", version = "1")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderStatusHistories(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderStatusHistories(id)));
    }

    @PostMapping(version = "1")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(orderService.createOrder(request)));
    }

    @PutMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @PathVariable Long id,
            @RequestBody @Valid UpdateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrder(id, request)));
    }

    @DeleteMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
