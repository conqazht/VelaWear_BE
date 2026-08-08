package vn.conganh.commercial.feature.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.UserGender;

/**
 * Integration test verifying that admin list queries for orders, payments,
 * and carts have bounded query counts regardless of page size (BE-008).
 *
 * <p>Uses Hibernate statistics to measure actual query counts on PostgreSQL.
 * Asserts that to-one associations ({@code Order.user}, {@code Payment.order},
 * {@code Cart.user}) are initialized via {@code @EntityGraph} without N+1
 * lazy loads.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(
        statements = "TRUNCATE TABLE inventory_logs, coupon_usages, payment_transactions, payments, "
                + "order_status_histories, order_items, orders, cart_items, carts, coupons, product_images, "
                + "product_variants, products, users RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@DisplayName("Admin commerce list N+1 query elimination (BE-008)")
class AdminCommerceListFetchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionFactory sessionFactory;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
    }

    /**
     * Returns a no-op Specification (WHERE 1=1) to simulate "no filter" in admin list calls.
     * Avoids ambiguity with {@code Specification.where(null)} in Spring Data JPA 3.5+.
     */
    private static <T> Specification<T> noFilter() {
        return (root, query, cb) -> cb.conjunction();
    }

    // --- Fixture helpers ---

    private User createUser(String suffix) {
        User user = new User();
        user.setEmail("admin-list-" + suffix + "@example.com");
        user.setFullName("Admin List User " + suffix);
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(UserGender.OTHER);
        return userRepository.save(user);
    }

    private Order createOrder(User user, String orderCode) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(orderCode);
        order.setStatus("PENDING");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(100));
        order.setReceiverName("Receiver " + orderCode);
        order.setReceiverPhone("0123456789");
        order.setReceiverAddress("123 Test Street");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("UNPAID");
        return orderRepository.save(order);
    }

    private Payment createPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.COD);
        payment.setAmount(order.getFinalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    // --- Order tests ---

    @Nested
    @DisplayName("Order admin list")
    class OrderAdminList {

        @Test
        @DisplayName("findAll with spec and page should eagerly fetch user — bounded query count")
        void orderList_queryCountBounded() {
            // Create 10 orders, each owned by a different user
            List<User> users = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                User user = createUser("order-" + i);
                users.add(user);
                createOrder(user, "BE008-ORD-" + i);
            }

            // --- Single row page ---
            stats.clear();
            Page<Order> singlePage = orderRepository.findAll(
                    noFilter(), PageRequest.of(0, 1));
            long singleQueries = stats.getQueryExecutionCount();

            assertThat(singlePage.getTotalElements()).isEqualTo(10);
            assertThat(singlePage.getContent()).hasSize(1);
            // Verify user is initialized (not a proxy that triggers lazy load)
            Order singleOrder = singlePage.getContent().getFirst();
            assertThat(singleOrder.getUser()).isNotNull();
            assertThat(singleOrder.getUser().getEmail()).isNotNull();

            // --- Full page (10 rows) ---
            stats.clear();
            Page<Order> fullPage = orderRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            long fullQueries = stats.getQueryExecutionCount();

            assertThat(fullPage.getContent()).hasSize(10);
            // Verify all users are initialized
            fullPage.getContent().forEach(order -> {
                assertThat(order.getUser()).isNotNull();
                assertThat(order.getUser().getFullName()).isNotNull();
            });

            // Query count should be bounded: the difference between
            // paging 1 row and 10 rows should be minimal (0-1 due to count query)
            long diff = fullQueries - singleQueries;
            assertTrue(diff <= 1,
                    "Order list query growth from 1 to 10 rows should be bounded. "
                            + "Single-row queries: " + singleQueries
                            + ", full-page queries: " + fullQueries
                            + ", difference: " + diff
                            + ". N+1 for Order.user is likely still present.");
        }

        @Test
        @DisplayName("empty page should work without errors")
        void orderList_emptyPage() {
            stats.clear();
            Page<Order> emptyPage = orderRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            assertThat(emptyPage.getContent()).isEmpty();
            assertThat(emptyPage.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("root entities should be unique (no cartesian product)")
        void orderList_uniqueRoots() {
            User user = createUser("dedup");
            for (int i = 0; i < 5; i++) {
                createOrder(user, "BE008-DEDUP-" + i);
            }
            Page<Order> page = orderRepository.findAll(
                    noFilter(), PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getContent()).hasSize(5);
            // All IDs should be unique
            assertThat(page.getContent().stream().map(Order::getId).distinct().count())
                    .isEqualTo(5);
        }
    }

    // --- Payment tests ---

    @Nested
    @DisplayName("Payment admin list")
    class PaymentAdminList {

        @Test
        @DisplayName("findAll with spec and page should eagerly fetch order — bounded query count")
        void paymentList_queryCountBounded() {
            // Create 10 payments, each linked to a different order
            User user = createUser("payment");
            for (int i = 0; i < 10; i++) {
                Order order = createOrder(user, "BE008-PAY-" + i);
                createPayment(order);
            }

            // --- Single row page ---
            stats.clear();
            Page<Payment> singlePage = paymentRepository.findAll(
                    noFilter(), PageRequest.of(0, 1));
            long singleQueries = stats.getQueryExecutionCount();

            assertThat(singlePage.getTotalElements()).isEqualTo(10);
            assertThat(singlePage.getContent()).hasSize(1);
            Payment singlePayment = singlePage.getContent().getFirst();
            assertThat(singlePayment.getOrder()).isNotNull();
            assertThat(singlePayment.getOrder().getOrderCode()).isNotNull();

            // --- Full page (10 rows) ---
            stats.clear();
            Page<Payment> fullPage = paymentRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            long fullQueries = stats.getQueryExecutionCount();

            assertThat(fullPage.getContent()).hasSize(10);
            fullPage.getContent().forEach(payment -> {
                assertThat(payment.getOrder()).isNotNull();
                assertThat(payment.getOrder().getStatus()).isNotNull();
            });

            long diff = fullQueries - singleQueries;
            assertTrue(diff <= 1,
                    "Payment list query growth from 1 to 10 rows should be bounded. "
                            + "Single-row queries: " + singleQueries
                            + ", full-page queries: " + fullQueries
                            + ", difference: " + diff
                            + ". N+1 for Payment.order is likely still present.");
        }

        @Test
        @DisplayName("empty page should work without errors")
        void paymentList_emptyPage() {
            stats.clear();
            Page<Payment> emptyPage = paymentRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            assertThat(emptyPage.getContent()).isEmpty();
            assertThat(emptyPage.getTotalElements()).isZero();
        }
    }

    // --- Cart tests ---

    @Nested
    @DisplayName("Cart admin list")
    class CartAdminList {

        @Test
        @DisplayName("findAll with spec and page should eagerly fetch user — bounded query count")
        void cartList_queryCountBounded() {
            // Create 10 carts, each owned by a different user
            for (int i = 0; i < 10; i++) {
                User user = createUser("cart-" + i);
                createCart(user);
            }

            // --- Single row page ---
            stats.clear();
            Page<Cart> singlePage = cartRepository.findAll(
                    noFilter(), PageRequest.of(0, 1));
            long singleQueries = stats.getQueryExecutionCount();

            assertThat(singlePage.getTotalElements()).isEqualTo(10);
            assertThat(singlePage.getContent()).hasSize(1);
            Cart singleCart = singlePage.getContent().getFirst();
            assertThat(singleCart.getUser()).isNotNull();
            assertThat(singleCart.getUser().getEmail()).isNotNull();

            // --- Full page (10 rows) ---
            stats.clear();
            Page<Cart> fullPage = cartRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            long fullQueries = stats.getQueryExecutionCount();

            assertThat(fullPage.getContent()).hasSize(10);
            fullPage.getContent().forEach(cart -> {
                assertThat(cart.getUser()).isNotNull();
                assertThat(cart.getUser().getFullName()).isNotNull();
            });

            long diff = fullQueries - singleQueries;
            assertTrue(diff <= 1,
                    "Cart list query growth from 1 to 10 rows should be bounded. "
                            + "Single-row queries: " + singleQueries
                            + ", full-page queries: " + fullQueries
                            + ", difference: " + diff
                            + ". N+1 for Cart.user is likely still present.");
        }

        @Test
        @DisplayName("empty page should work without errors")
        void cartList_emptyPage() {
            stats.clear();
            Page<Cart> emptyPage = cartRepository.findAll(
                    noFilter(), PageRequest.of(0, 10));
            assertThat(emptyPage.getContent()).isEmpty();
            assertThat(emptyPage.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("root entities should be unique (no cartesian product)")
        void cartList_uniqueRoots() {
            for (int i = 0; i < 5; i++) {
                User user = createUser("cart-dedup-" + i);
                createCart(user);
            }
            Page<Cart> page = cartRepository.findAll(
                    noFilter(), PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getContent()).hasSize(5);
            assertThat(page.getContent().stream().map(Cart::getId).distinct().count())
                    .isEqualTo(5);
        }
    }
}
