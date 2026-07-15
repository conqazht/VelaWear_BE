package vn.conganh.commercial.feature.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.exception.CouponNotValidException;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.cart.CartItemRepository;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderStatusHistoryRepository;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;
import vn.conganh.commercial.util.constant.UserGender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Sql(
        statements = "TRUNCATE TABLE inventory_logs, coupon_usages, payment_transactions, payments, "
                + "order_status_histories, order_items, orders, cart_items, carts, coupons, product_images, "
                + "product_variants, products, users RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class CheckoutConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private User testUser;
    private User secondUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("concurrency.test@example.com");
        testUser.setFullName("Concurrency Test User");
        testUser.setPassword("password123");
        testUser.setBirthDate(LocalDate.of(1995, 1, 1));
        testUser.setGender(UserGender.OTHER);
        testUser = userRepository.save(testUser);

        secondUser = new User();
        secondUser.setEmail("concurrency.test.2@example.com");
        secondUser.setFullName("Second Concurrency Test User");
        secondUser.setPassword("password123");
        secondUser.setBirthDate(LocalDate.of(1996, 1, 1));
        secondUser.setGender(UserGender.OTHER);
        secondUser = userRepository.save(secondUser);

        testProduct = new Product();
        testProduct.setCategoryId(1L);
        testProduct.setBrandId(1L);
        testProduct.setName("Concurrency Product");
        testProduct.setSlug("concurrency-product");
        testProduct.setDescription("A product for concurrency testing");
        testProduct.setStatus("ACTIVE");
        testProduct = productRepository.save(testProduct);
    }

    private void addCartItem(User user, ProductVariant variant, int quantity) {
        Cart cart = new Cart();
        cart.setUser(user);
        cart = cartRepository.save(cart);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setVariantId(variant.getId());
        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);
    }

    // Task 5.4
    @Test
    @DisplayName("Should prevent overselling when concurrent checkouts happen for stock = 1")
    void checkout_concurrentStock_preventsOverselling() throws Exception {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(testProduct);
        variant.setSku("CONCURRENT-STOCK-1");
        variant.setPrice(BigDecimal.valueOf(100));
        variant.setStockQuantity(1);
        variant.setStatus("ACTIVE");
        variant = productVariantRepository.save(variant);

        addCartItem(testUser, variant, 1);
        addCartItem(secondUser, variant, 1);

        CheckoutRequest firstRequest = new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                BigDecimal.ZERO,
                null,
                checkoutService.preview(
                        new CheckoutPreviewRequest("COD", null), testUser.getEmail()).pricingFingerprint()
        );
        CheckoutRequest secondRequest = new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                BigDecimal.ZERO,
                null,
                checkoutService.preview(
                        new CheckoutPreviewRequest("COD", null), secondUser.getEmail()).pricingFingerprint());

        Callable<CheckoutResponse> firstBuyerTask = () -> checkoutService.checkout(firstRequest, testUser.getEmail());
        Callable<CheckoutResponse> secondBuyerTask = () -> checkoutService.checkout(secondRequest, secondUser.getEmail());
        
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CheckoutResponse> future1 = executorService.submit(firstBuyerTask);
            Future<CheckoutResponse> future2 = executorService.submit(secondBuyerTask);

            int successCount = 0;
            int insufficientStockErrorCount = 0;

            try {
                future1.get();
                successCount++;
            } catch (Exception e) {
                if (e.getCause() instanceof CodedBusinessException coded
                        && "INSUFFICIENT_STOCK".equals(coded.getCode())) {
                    insufficientStockErrorCount++;
                }
            }

            try {
                future2.get();
                successCount++;
            } catch (Exception e) {
                if (e.getCause() instanceof CodedBusinessException coded
                        && "INSUFFICIENT_STOCK".equals(coded.getCode())) {
                    insufficientStockErrorCount++;
                }
            }

            assertEquals(1, successCount, "Only one checkout should succeed");
            assertEquals(1, insufficientStockErrorCount,
                    "One checkout should fail with INSUFFICIENT_STOCK");
        }

        ProductVariant updatedVariant = productVariantRepository.findById(variant.getId()).orElseThrow();
        assertEquals(0, updatedVariant.getStockQuantity(), "Stock should be reduced to 0");
    }

    // Task 5.5
    @Test
    @DisplayName("Should prevent reusing single-use coupon in concurrent checkouts")
    void checkout_concurrentCoupon_preventsReuse() throws Exception {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(testProduct);
        variant.setSku("CONCURRENT-COUPON-1");
        variant.setPrice(BigDecimal.valueOf(100));
        variant.setStockQuantity(10); // Plenty of stock
        variant.setStatus("ACTIVE");
        variant = productVariantRepository.save(variant);

        addCartItem(testUser, variant, 1);
        addCartItem(secondUser, variant, 1);

        Coupon coupon = new Coupon();
        coupon.setCode("LIMITED1");
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(BigDecimal.valueOf(10));
        coupon.setUsageLimit(1);
        coupon.setUsedCount(0);
        coupon.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        coupon.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon = couponRepository.save(coupon);

        CheckoutRequest firstRequest = new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                BigDecimal.ZERO,
                coupon.getCode(),
                checkoutService.preview(
                        new CheckoutPreviewRequest("COD", coupon.getCode()),
                        testUser.getEmail()).pricingFingerprint()
        );
        CheckoutRequest secondRequest = new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                BigDecimal.ZERO,
                coupon.getCode(),
                checkoutService.preview(
                        new CheckoutPreviewRequest("COD", coupon.getCode()),
                        secondUser.getEmail()).pricingFingerprint());

        Callable<CheckoutResponse> firstBuyerTask = () -> checkoutService.checkout(firstRequest, testUser.getEmail());
        Callable<CheckoutResponse> secondBuyerTask = () -> checkoutService.checkout(secondRequest, secondUser.getEmail());
        
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CheckoutResponse> future1 = executorService.submit(firstBuyerTask);
            Future<CheckoutResponse> future2 = executorService.submit(secondBuyerTask);

            int successCount = 0;
            int couponNotValidExceptionCount = 0;

            try {
                future1.get();
                successCount++;
            } catch (Exception e) {
                if (e.getCause() instanceof CouponNotValidException) {
                    couponNotValidExceptionCount++;
                }
            }

            try {
                future2.get();
                successCount++;
            } catch (Exception e) {
                if (e.getCause() instanceof CouponNotValidException) {
                    couponNotValidExceptionCount++;
                }
            }

            assertEquals(1, successCount, "Only one checkout should succeed");
            assertTrue(couponNotValidExceptionCount >= 1, "At least one checkout should fail with CouponNotValidException");
        }

        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertEquals(1, updatedCoupon.getUsedCount(), "Coupon usedCount should be exactly 1");
    }
}
