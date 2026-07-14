package vn.conganh.commercial.feature.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.order.dto.OrderFilterRequest;
import vn.conganh.commercial.feature.order.dto.OrderResponse;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryResponse;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Order - OrderServiceImpl")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, userRepository, orderStatusHistoryRepository, orderItemRepository);
    }

    @Nested
    @DisplayName("Create order")
    class CreateOrder {

        @Test
        @DisplayName("createOrder - tạo order thành công và dùng status mặc định")
        void createOrder_validRequest_returnsOrderResponseWithDefaults() {
            // Arrange
            User user = user(1L);
            CreateOrderRequest request = createRequest(null, null, null, null);
            when(orderRepository.existsByOrderCode("ORD-001")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 10L);
                return order;
            });

            // Act
            OrderResponse response = orderService.createOrder(request);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.shippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.paymentStatus()).isEqualTo("UNPAID");
        }

        @Test
        @DisplayName("createOrder - không gọi save khi order code đã tồn tại")
        void createOrder_duplicateOrderCode_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateOrderRequest request = createRequest("PENDING", BigDecimal.ZERO, BigDecimal.ZERO, "UNPAID");
            when(orderRepository.existsByOrderCode("ORD-001")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Order code already exists");
            verify(userRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("createOrder - không gọi save khi user không tồn tại")
        void createOrder_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateOrderRequest request = createRequest("PENDING", BigDecimal.ZERO, BigDecimal.ZERO, "UNPAID");
            when(orderRepository.existsByOrderCode("ORD-001")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read order")
    class ReadOrder {

        @Test
        @DisplayName("getOrderById - trả về order khi id tồn tại")
        void getOrderById_existingOrder_returnsOrderResponse() {
            // Arrange
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, user(1L), "PENDING")));

            // Act
            OrderResponse response = orderService.getOrderById(10L);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.orderCode()).isEqualTo("ORD-10");
        }

        @Test
        @DisplayName("getOrderByOrderCode - ném ResourceNotFoundException khi không tìm thấy order")
        void getOrderByOrderCode_missingOrder_throwsResourceNotFoundException() {
            // Arrange
            when(orderRepository.findByOrderCode("ORD-404")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.getOrderByOrderCode("ORD-404"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getOrderStatusHistories - trả về lịch sử trạng thái khi order tồn tại")
        void getOrderStatusHistories_existingOrder_returnsHistoryResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Order order = order(10L, user(1L), "SHIPPING");
            when(orderRepository.existsById(10L)).thenReturn(true);
            when(orderStatusHistoryRepository.findAll(ArgumentMatchers.<Specification<OrderStatusHistory>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(history(100L, order, "PENDING", "SHIPPING")), pageable, 1));

            // Act
            ResultPaginationDTO responses = orderService.getOrderStatusHistories(10L, null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("fromStatus").containsExactly("PENDING");
            assertThat(responses.result()).extracting("toStatus").containsExactly("SHIPPING");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getOrdersByUserId - từ chối query userId khác path userId")
        void getOrdersByUserId_conflictingQueryUserId_throwsInvalidRequestException() {
            // Arrange
            OrderFilterRequest filter = new OrderFilterRequest(
                    2L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.TEN,
                    null,
                    null,
                    null,
                    null);

            // Act & Assert
            assertThatThrownBy(() -> orderService.getOrdersByUserId(1L, filter, PageRequest.of(0, 10)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("userId");
            verifyNoInteractions(orderRepository, userRepository, orderStatusHistoryRepository);
        }
    }

    @Nested
    @DisplayName("Update order")
    class UpdateOrder {

        @Test
        @DisplayName("updateOrder - cập nhật order và lưu history khi status thay đổi")
        void updateOrder_statusChanged_savesOrderAndStatusHistory() {
            // Arrange
            Order order = order(10L, user(1L), "PENDING");
            UpdateOrderRequest request = updateRequest("SHIPPING");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            // Act
            OrderResponse response = orderService.updateOrder(10L, request);

            // Assert
            assertThat(response.status()).isEqualTo("SHIPPING");
            assertThat(response.receiverName()).isEqualTo("Tran Thi B");
            verify(orderStatusHistoryRepository).save(argThat(history ->
                    history.getOrder().equals(order)
                            && "PENDING".equals(history.getFromStatus())
                            && "SHIPPING".equals(history.getToStatus())));
        }

        @Test
        @DisplayName("updateOrder - không lưu history khi status không đổi")
        void updateOrder_statusUnchanged_doesNotSaveStatusHistory() {
            // Arrange
            Order order = order(10L, user(1L), "PENDING");
            UpdateOrderRequest request = updateRequest("PENDING");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            // Act
            orderService.updateOrder(10L, request);

            // Assert
            verify(orderStatusHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete order")
    class DeleteOrder {

        @Test
        @DisplayName("deleteOrder - xóa order khi id tồn tại")
        void deleteOrder_existingOrder_deletesOrder() {
            // Arrange
            Order order = order(10L, user(1L), "PENDING");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

            // Act
            orderService.deleteOrder(10L);

            // Assert
            verify(orderRepository).delete(order);
        }
    }

    private CreateOrderRequest createRequest(
            String status,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            String paymentStatus) {
        return new CreateOrderRequest(
                1L,
                "ORD-001",
                status,
                BigDecimal.valueOf(100000),
                shippingFee,
                discountAmount,
                BigDecimal.valueOf(100000),
                "Nguyen Van A",
                "0123456789",
                "123 Le Loi",
                "COD",
                paymentStatus);
    }

    private UpdateOrderRequest updateRequest(String status) {
        return new UpdateOrderRequest(
                status,
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(110000),
                "Tran Thi B",
                "0987654321",
                "456 Xuan Thuy",
                "VNPAY",
                "PAID");
    }

    private OrderStatusHistory history(Long id, Order order, String fromStatus, String toStatus) {
        OrderStatusHistory history = new OrderStatusHistory();
        ReflectionTestUtils.setField(history, "id", id);
        history.setOrder(order);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        return history;
    }

    private Order order(Long id, User user, String status) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setUser(user);
        order.setOrderCode("ORD-" + id);
        order.setStatus(status);
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(100000));
        order.setReceiverName("Nguyen Van A");
        order.setReceiverPhone("0123456789");
        order.setReceiverAddress("123 Le Loi");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("UNPAID");
        return order;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }
}
