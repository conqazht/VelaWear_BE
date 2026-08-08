package vn.conganh.commercial.feature.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.cart.CartItemRepository;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderService;
import vn.conganh.commercial.feature.order.dto.UpdateOrderRequest;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.feature.payment.sepay.SePayIpnRequest;
import vn.conganh.commercial.feature.payment.sepay.SePayProperties;
import vn.conganh.commercial.feature.payment.sepay.SePayService;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.product.ProductService;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.SaleAllocation;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignType;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignService;
import vn.conganh.commercial.feature.salecampaign.dto.CreateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.IncreaseQuotaRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignItemRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleDisplayRequest;
import vn.conganh.commercial.feature.salecampaign.SaleCustomerUsage;
import vn.conganh.commercial.feature.salecampaign.SaleCustomerUsageRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.PaymentTransactionStatus;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;
import vn.conganh.commercial.util.constant.UserGender;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(
        statements = "TRUNCATE TABLE sale_allocations, sale_customer_usages, sale_campaign_items, sale_campaigns, "
                + "inventory_logs, coupon_usages, payment_transactions, payments, order_status_histories, "
                + "order_items, orders, cart_items, carts, coupons, product_images, product_variants, "
                + "products, users RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Import(SaleCampaignConcurrencyIntegrationTest.ContentionTestConfiguration.class)
class SaleCampaignConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final long RACE_TIMEOUT_SECONDS = 15;

    @Autowired CheckoutService checkoutService;
    @Autowired OrderResourceLifecycleService lifecycleService;
    @Autowired SePayService sePayService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductService productService;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SaleCampaignRepository campaignRepository;
    @Autowired SaleCampaignService saleCampaignService;
    @Autowired SaleCampaignItemRepository campaignItemRepository;
    @Autowired SaleCustomerUsageRepository usageRepository;
    @Autowired SaleAllocationRepository allocationRepository;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentTransactionRepository transactionRepository;
    @Autowired InventoryLogRepository inventoryLogRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SePayProperties sePayProperties;
    @Autowired RepositoryContentionGate repositoryContentionGate;

    private User firstUser;
    private User secondUser;
    private Product product;

    @BeforeEach
    void setUp() {
        repositoryContentionGate.clear();
        sePayProperties.setEnabled(true);
        sePayProperties.setEnvironment("production");
        sePayProperties.setMerchantId("SP-LIVE-INTEGRATION-TEST");
        sePayProperties.setSecretKey("spsk_live_integration_test_secret");
        sePayProperties.setCheckoutUrl("https://pay.sepay.vn/v1/checkout/init");
        firstUser = createUser("flash-a@example.com");
        secondUser = createUser("flash-b@example.com");
        product = new Product();
        product.setCategoryId(1L);
        product.setBrandId(1L);
        product.setName("Flash concurrency product");
        product.setSlug("flash-concurrency-product");
        product.setDescription("Concurrency test");
        product.setStatus("ACTIVE");
        product = productRepository.save(product);
    }

    @Test
    void twoBuyersCompetingForLastFlashQuota_onlyOneKeepsFlashReservation() throws Exception {
        ProductVariant variant = createVariant("FLASH-LAST", 10);
        SaleCampaignItem saleItem = createFlash(variant, 1, 1);
        addCartItem(firstUser, variant, 1);
        addCartItem(secondUser, variant, 1);

        String firstFingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null), firstUser.getEmail()).pricingFingerprint();
        String secondFingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null), secondUser.getEmail()).pricingFingerprint();

        CheckoutRequest firstRequest = request("COD", firstFingerprint);
        CheckoutRequest secondRequest = request("COD", secondFingerprint);
        List<Result> results = race(
                () -> checkoutService.checkout(firstRequest, firstUser.getEmail(), "last-a"),
                () -> checkoutService.checkout(secondRequest, secondUser.getEmail(), "last-b"));

        assertThat(results.stream().filter(Result::success)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.success()).map(Result::errorCode).toList())
                .allMatch(code -> code.equals("FLASH_SALE_SOLD_OUT") || code.equals("PRICE_CHANGED"));

        SaleCampaignItem reloaded = campaignItemRepository.findById(saleItem.getId()).orElseThrow();
        assertEquals(0, reloaded.getReservedQuantity());
        assertEquals(1, reloaded.getSoldQuantity());
        assertEquals(1, allocationRepository.count());
        assertEquals(9, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void customerAtFlashLimit_fallsBackToBaseWithoutIncreasingPurchasedUsage() {
        ProductVariant variant = createVariant("FLASH-LIMIT", 10);
        SaleCampaignItem saleItem = createFlash(variant, 10, 1);
        Cart cart = addCartItem(firstUser, variant, 1);

        var firstPreview = checkoutService.preview(new CheckoutPreviewRequest("COD", null), firstUser.getEmail());
        CheckoutResponse first = checkoutService.checkout(
                request("COD", firstPreview.pricingFingerprint()), firstUser.getEmail(), "limit-first");
        assertThat(first.items().getFirst().priceSource()).isEqualTo(PriceSource.FLASH_SALE);

        CartItem again = new CartItem();
        again.setCart(cart);
        again.setVariantId(variant.getId());
        again.setQuantity(1);
        cartItemRepository.save(again);
        var secondPreview = checkoutService.preview(new CheckoutPreviewRequest("COD", null), firstUser.getEmail());
        assertThat(secondPreview.items().getFirst().priceSource()).isEqualTo(PriceSource.BASE);
        CheckoutResponse second = checkoutService.checkout(
                request("COD", secondPreview.pricingFingerprint()), firstUser.getEmail(), "limit-second");
        assertThat(second.items().getFirst().priceSource()).isEqualTo(PriceSource.BASE);

        SaleCustomerUsage usage = usageRepository
                .findByCampaignItemIdAndUserId(saleItem.getId(), firstUser.getId()).orElseThrow();
        assertEquals(0, usage.getReservedQuantity());
        assertEquals(1, usage.getPurchasedQuantity());
    }

    @Test
    void concurrentPerCustomerCounter_allowsOnlyOneReservationAtTheLimit() throws Exception {
        ProductVariant variant = createVariant("FLASH-LIMIT-RACE", 10);
        SaleCampaignItem saleItem = createFlash(variant, 10, 1);
        SaleCustomerUsage usage = new SaleCustomerUsage();
        usage.setCampaignItem(saleItem);
        usage.setUser(firstUser);
        usageRepository.saveAndFlush(usage);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Integer> updatedRows = raceValues(
                () -> transaction.execute(status -> usageRepository.reserveWithinLimit(
                        saleItem.getId(), firstUser.getId(), 1, 1)),
                () -> transaction.execute(status -> usageRepository.reserveWithinLimit(
                        saleItem.getId(), firstUser.getId(), 1, 1)));

        assertThat(updatedRows).containsExactlyInAnyOrder(0, 1);
        SaleCustomerUsage reloaded = usageRepository
                .findByCampaignItemIdAndUserId(saleItem.getId(), firstUser.getId()).orElseThrow();
        assertEquals(1, reloaded.getReservedQuantity());
        assertEquals(0, reloaded.getPurchasedQuantity());
    }

    @Test
    void stockFailure_rollsBackFlashQuotaUsageAndOrder() {
        ProductVariant variant = createVariant("FLASH-ROLLBACK", 1);
        SaleCampaignItem saleItem = createFlash(variant, 10, 5);
        addCartItem(firstUser, variant, 2);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null), firstUser.getEmail()).pricingFingerprint();

        CodedBusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                CodedBusinessException.class,
                () -> checkoutService.checkout(
                        request("COD", fingerprint), firstUser.getEmail(), "stock-rollback"));
        assertEquals("INSUFFICIENT_STOCK", error.getCode());
        SaleCampaignItem reloaded = campaignItemRepository.findById(saleItem.getId()).orElseThrow();
        assertEquals(0, reloaded.getReservedQuantity());
        assertEquals(0, reloaded.getSoldQuantity());
        assertEquals(0, usageRepository.count());
        assertEquals(0, orderRepository.count());
        assertEquals(1, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void couponBenefitChangedAfterPreview_isRejectedAsPriceChanged() {
        ProductVariant variant = createVariant("COUPON-FINGERPRINT", 5);
        addCartItem(firstUser, variant, 1);
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setType(CouponType.PERCENTAGE);
        coupon.setValue(new BigDecimal("10.00"));
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartDate(Instant.now().minusSeconds(60));
        coupon.setEndDate(Instant.now().plusSeconds(3600));
        coupon = couponRepository.saveAndFlush(coupon);

        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", coupon.getCode()), firstUser.getEmail()).pricingFingerprint();
        coupon.setValue(new BigDecimal("20.00"));
        couponRepository.saveAndFlush(coupon);

        CodedBusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                CodedBusinessException.class,
                () -> checkoutService.checkout(
                        new CheckoutRequest(
                                "Receiver", "0123456789", "Address", "COD",
                                BigDecimal.ZERO, "SAVE10", fingerprint),
                        firstUser.getEmail(),
                        "coupon-price-changed"));

        assertEquals("PRICE_CHANGED", error.getCode());
        assertEquals(0, orderRepository.count());
        assertEquals(5, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void sameIdempotencyKey_returnsSameOrderAndDoesNotConsumeTwice() {
        ProductVariant variant = createVariant("FLASH-IDEMPOTENT", 5);
        SaleCampaignItem saleItem = createFlash(variant, 5, 2);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutRequest checkoutRequest = request("COD", fingerprint);

        CheckoutResponse first = checkoutService.checkout(checkoutRequest, firstUser.getEmail(), "same-key");
        CheckoutResponse replay = checkoutService.checkout(checkoutRequest, firstUser.getEmail(), "same-key");

        assertEquals(first.orderId(), replay.orderId());
        assertEquals(1, orderRepository.count());
        assertEquals(4, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getSoldQuantity());
    }

    @Test
    void sepayIdempotencyReplay_returnsTheSamePaymentInitiationForm() {
        ProductVariant variant = createVariant("SEPAY-IDEMPOTENT", 5);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutRequest checkoutRequest = request("SEPAY", fingerprint);

        CheckoutResponse first = checkoutService.checkout(checkoutRequest, firstUser.getEmail(), "sepay-same-key");
        CheckoutResponse replay = checkoutService.checkout(checkoutRequest, firstUser.getEmail(), "sepay-same-key");

        assertEquals(first.orderId(), replay.orderId());
        assertEquals(first.paymentId(), replay.paymentId());
        assertThat(first.paymentInitiation()).isNotNull();
        assertThat(replay.paymentInitiation()).isEqualTo(first.paymentInitiation());
        assertEquals(1, orderRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals(4, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @DisplayName("Hai checkout SePay cùng idempotency key chỉ tạo một order và reservation")
    void concurrentSepayCheckoutWithSameIdempotencyKey_createsOneOrderPaymentAndReservation() throws Exception {
        ProductVariant variant = createVariant("SEPAY-IDEMPOTENT-RACE", 5);
        SaleCampaignItem saleItem = createFlash(variant, 5, 2);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutRequest checkoutRequest = request("SEPAY", fingerprint);
        repositoryContentionGate.armCart(firstUser.getId());

        List<Result> results = race(
                () -> checkoutService.checkout(
                        checkoutRequest, firstUser.getEmail(), "sepay-concurrent-same-key"),
                () -> checkoutService.checkout(
                        checkoutRequest, firstUser.getEmail(), "sepay-concurrent-same-key"));

        assertThat(results).allMatch(Result::success);
        CheckoutResponse first = results.getFirst().response();
        CheckoutResponse second = results.getLast().response();
        assertEquals(first.orderId(), second.orderId());
        assertEquals(first.paymentId(), second.paymentId());
        assertThat(first.paymentInitiation()).isNotNull();
        assertThat(second.paymentInitiation()).isEqualTo(first.paymentInitiation());

        assertEquals(1, orderRepository.count());
        assertEquals(1, orderItemRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals(0, transactionRepository.count());
        assertEquals(1, allocationRepository.count());
        assertEquals(1, usageRepository.count());
        assertEquals(1, inventoryLogRepository.count());
        assertEquals(0, cartItemRepository.count());
        assertEquals(4, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());

        SaleCampaignItem reloadedItem = campaignItemRepository.findById(saleItem.getId()).orElseThrow();
        assertEquals(1, reloadedItem.getReservedQuantity());
        assertEquals(0, reloadedItem.getSoldQuantity());
        assertEquals(SaleAllocationStatus.RESERVED, allocationRepository.findAll().getFirst().getStatus());
        SaleCustomerUsage usage = usageRepository
                .findByCampaignItemIdAndUserId(saleItem.getId(), firstUser.getId()).orElseThrow();
        assertEquals(1, usage.getReservedQuantity());
        assertEquals(0, usage.getPurchasedQuantity());
    }

    @Test
    void expiredSepayIdempotencyReplay_releasesOnceAndDoesNotReissuePaymentForm() {
        ProductVariant variant = createVariant("SEPAY-EXPIRED-REPLAY", 2);
        SaleCampaignItem saleItem = createFlash(variant, 2, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutRequest checkoutRequest = request("SEPAY", fingerprint);
        CheckoutResponse first = checkoutService.checkout(
                checkoutRequest, firstUser.getEmail(), "sepay-expired-key");
        Order order = orderRepository.findById(first.orderId()).orElseThrow();
        order.setPaymentDueAt(Instant.now().minusSeconds(31));
        order.setReservationExpiresAt(Instant.now().minusSeconds(1));
        orderRepository.saveAndFlush(order);

        CheckoutResponse replay = checkoutService.checkout(
                checkoutRequest, firstUser.getEmail(), "sepay-expired-key");

        assertEquals(first.orderId(), replay.orderId());
        assertThat(replay.paymentInitiation()).isNull();
        assertEquals("CANCELLED", replay.status());
        assertEquals("FAILED", replay.paymentStatus());
        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findByOrderId(first.orderId()).orElseThrow().getStatus());
        assertEquals(2, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getReservedQuantity());
    }

    @Test
    void increaseQuotaRacingCheckout_doesNotOverwriteSoldCounters() throws Exception {
        ProductVariant variant = createVariant("FLASH-QUOTA-INCREASE", 5);
        SaleCampaignItem saleItem = createFlash(variant, 1, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null), firstUser.getEmail()).pricingFingerprint();
        long version = saleItem.getCampaign().getVersion();

        raceVoid(
                () -> checkoutService.checkout(
                        request("COD", fingerprint), firstUser.getEmail(), "quota-increase-checkout"),
                () -> saleCampaignService.increaseQuota(
                        saleItem.getCampaign().getId(), saleItem.getId(), new IncreaseQuotaRequest(version, 1)));

        SaleCampaignItem reloaded = campaignItemRepository.findById(saleItem.getId()).orElseThrow();
        assertEquals(2, reloaded.getQuota());
        assertEquals(0, reloaded.getReservedQuantity());
        assertEquals(1, reloaded.getSoldQuantity());
    }

    @Test
    void campaignMutationResponse_containsPostFlushVersion() {
        ProductVariant variant = createVariant("FLASH-VERSION", 5);
        SaleCampaignItem saleItem = createFlash(variant, 5, 1);
        long previousVersion = saleItem.getCampaign().getVersion();

        var response = saleCampaignService.updateDisplay(
                saleItem.getCampaign().getId(),
                new UpdateSaleDisplayRequest(previousVersion, "Updated live campaign", "Description", null));

        assertEquals(previousVersion + 1, response.version());
        assertEquals("Updated live campaign", response.name());
    }

    @Test
    void createCampaign_withWhitespaceAndCaseVariantOfExistingCode_isRejectedWithoutInserting() {
        ProductVariant variant = createVariant("FLASH-CODE-CREATE", 5);
        saleCampaignService.create(campaignRequest("FLASH-CODE", variant), firstUser.getEmail());
        long campaignCount = campaignRepository.count();

        assertThatThrownBy(() -> saleCampaignService.create(
                        campaignRequest("  flash-code  ", variant), firstUser.getEmail()))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Sale campaign code already exists");

        assertThat(campaignRepository.count()).isEqualTo(campaignCount);
    }

    @Test
    void updateCampaign_withWhitespaceAndCaseVariantOfAnotherCode_isRejectedWithoutChangingCampaigns() {
        ProductVariant variant = createVariant("FLASH-CODE-UPDATE", 5);
        saleCampaignService.create(campaignRequest("EXISTING-CODE", variant), firstUser.getEmail());
        var target = saleCampaignService.create(campaignRequest("TARGET-CODE", variant), firstUser.getEmail());
        long campaignCount = campaignRepository.count();

        assertThatThrownBy(() -> saleCampaignService.update(
                        target.id(), updateCampaignRequest(target.version(), "  existing-code  ", variant)))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("Sale campaign code already exists");

        assertThat(campaignRepository.count()).isEqualTo(campaignCount);
        assertThat(campaignRepository.findById(target.id()).orElseThrow().getCode()).isEqualTo("TARGET-CODE");
    }

    @Test
    void productParentInOutstandingCampaign_cannotBeDeleted() {
        ProductVariant variant = createVariant("FLASH-PARENT-GUARD", 5);
        createFlash(variant, 5, 1);

        org.junit.jupiter.api.Assertions.assertThrows(
                vn.conganh.commercial.exception.InvalidRequestException.class,
                () -> productService.deleteProduct(product.getId()));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void adminCancellation_usesResourceLifecycleInsteadOfBypassingRelease() {
        ProductVariant variant = createVariant("FLASH-ADMIN-CANCEL", 2);
        SaleCampaignItem saleItem = createFlash(variant, 2, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutResponse checkout = checkoutService.checkout(
                request("SEPAY", fingerprint), firstUser.getEmail(), "admin-cancel");

        orderService.updateOrder(
                checkout.orderId(),
                new UpdateOrderRequest("CANCELLED", null, null, null, null, null, null, null, null));

        assertEquals(2, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getSoldQuantity());
        assertThat(orderRepository.findById(checkout.orderId()).orElseThrow().getResourcesReleasedAt()).isNotNull();
        assertEquals(PaymentStatus.CANCELLED,
                paymentRepository.findByOrderId(checkout.orderId()).orElseThrow().getStatus());
    }

    @Test
    void genericOrderUpdate_cannotBypassCheckoutPaymentLifecycle() {
        ProductVariant variant = createVariant("FLASH-PAYMENT-GUARD", 2);
        SaleCampaignItem saleItem = createFlash(variant, 2, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutResponse checkout = checkoutService.checkout(
                request("SEPAY", fingerprint), firstUser.getEmail(), "payment-guard");

        org.junit.jupiter.api.Assertions.assertThrows(
                vn.conganh.commercial.exception.InvalidRequestException.class,
                () -> orderService.updateOrder(
                        checkout.orderId(),
                        new UpdateOrderRequest(null, null, null, null, null, null, null, null, "PAID")));
        org.junit.jupiter.api.Assertions.assertThrows(
                vn.conganh.commercial.exception.InvalidRequestException.class,
                () -> orderService.updateOrder(
                        checkout.orderId(),
                        new UpdateOrderRequest("CONFIRMED", null, null, null, null, null, null, null, null)));

        assertEquals("UNPAID", orderRepository.findById(checkout.orderId()).orElseThrow().getPaymentStatus());
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findByOrderId(checkout.orderId()).orElseThrow().getStatus());
        assertEquals(1, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getReservedQuantity());
        assertEquals(SaleAllocationStatus.RESERVED,
                allocationRepository.findAll().getFirst().getStatus());
    }

    @Test
    void lateIpnRacingExpiry_releasesResourcesOnceAndMarksRefundPending() throws Exception {
        ProductVariant variant = createVariant("FLASH-LATE-IPN", 0);
        SaleCampaignItem saleItem = createFlash(variant, 5, 2);

        Order order = new Order();
        order.setUser(firstUser);
        order.setOrderCode("LATE-IPN-ORDER");
        order.setStatus("PENDING");
        order.setSubtotal(new BigDecimal("80.00"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(new BigDecimal("80.00"));
        order.setReceiverName("Receiver");
        order.setReceiverPhone("0123456789");
        order.setReceiverAddress("Address");
        order.setPaymentMethod("SEPAY");
        order.setPaymentStatus("UNPAID");
        order.setPaymentDueAt(Instant.now().minusSeconds(31));
        order.setReservationExpiresAt(Instant.now().minusSeconds(1));
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setVariantId(variant.getId());
        orderItem.setProductName(product.getName());
        orderItem.setSku(variant.getSku());
        orderItem.setListPrice(new BigDecimal("100.00"));
        orderItem.setPrice(new BigDecimal("80.00"));
        orderItem.setPriceSource(PriceSource.FLASH_SALE);
        orderItem.setSaleCampaignItem(saleItem);
        orderItem.setSaleCampaignCode(saleItem.getCampaign().getCode());
        orderItem.setSaleCampaignName(saleItem.getCampaign().getName());
        orderItem.setQuantity(1);
        orderItem.setSubtotal(new BigDecimal("80.00"));
        orderItem.setStatus("PENDING");
        orderItem = orderItemRepository.save(orderItem);

        saleItem.setReservedQuantity(1);
        campaignItemRepository.save(saleItem);
        SaleCustomerUsage usage = new SaleCustomerUsage();
        usage.setCampaignItem(saleItem);
        usage.setUser(firstUser);
        usage.setReservedQuantity(1);
        usageRepository.save(usage);
        SaleAllocation allocation = new SaleAllocation();
        allocation.setCampaignItem(saleItem);
        allocation.setOrderItem(orderItem);
        allocation.setUser(firstUser);
        allocation.setQuantity(1);
        allocation.setStatus(SaleAllocationStatus.RESERVED);
        allocationRepository.save(allocation);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setAmount(order.getFinalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        Long orderId = order.getId();
        SePayIpnRequest ipn = paidIpn(order.getOrderCode(), order.getFinalAmount());
        raceVoid(() -> lifecycleService.expireOrder(orderId), () -> sePayService.handleIpn(ipn));

        Order reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CANCELLED", reloadedOrder.getStatus());
        assertEquals("REFUND_PENDING", reloadedOrder.getPaymentStatus());
        assertThat(reloadedOrder.getResourcesReleasedAt()).isNotNull();
        assertEquals(PaymentStatus.REFUND_PENDING,
                paymentRepository.findByOrderId(orderId).orElseThrow().getStatus());
        assertEquals(1, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getReservedQuantity());
        assertEquals(1, inventoryLogRepository.count());
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void secondCapturedGatewayTransaction_isRecordedForRefundWithoutConfirmingTwice() {
        ProductVariant variant = createVariant("FLASH-DOUBLE-PAYMENT", 2);
        SaleCampaignItem saleItem = createFlash(variant, 2, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutResponse checkout = checkoutService.checkout(
                request("SEPAY", fingerprint), firstUser.getEmail(), "double-payment");
        SePayIpnRequest firstIpn = paidIpn(checkout.orderCode(), checkout.finalAmount());
        SePayIpnRequest secondIpn = paidIpn(checkout.orderCode(), checkout.finalAmount());

        sePayService.handleIpn(firstIpn);
        sePayService.handleIpn(secondIpn);

        Payment payment = paymentRepository.findByOrderId(checkout.orderId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());
        assertEquals(firstIpn.transaction().transaction_id(), payment.getTransactionCode());
        assertEquals("REFUND_PENDING",
                orderRepository.findById(checkout.orderId()).orElseThrow().getPaymentStatus());
        assertEquals(1, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getSoldQuantity());
        assertEquals(0, campaignItemRepository.findById(saleItem.getId()).orElseThrow().getReservedQuantity());
        assertEquals(1, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertThat(transactionRepository.findAll())
                .extracting(transaction -> transaction.getStatus())
                .containsExactlyInAnyOrder(
                        PaymentTransactionStatus.SUCCESS,
                        PaymentTransactionStatus.REFUND_PENDING);
    }

    @Test
    @DisplayName("Hai SePay IPN giống hệt nhau chỉ confirm payment và tài nguyên một lần")
    void concurrentExactDuplicateSepayIpn_confirmsPaymentAndResourcesOnce() throws Exception {
        ProductVariant variant = createVariant("FLASH-DUPLICATE-IPN-RACE", 2);
        SaleCampaignItem saleItem = createFlash(variant, 2, 1);
        addCartItem(firstUser, variant, 1);
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest("SEPAY", null), firstUser.getEmail()).pricingFingerprint();
        CheckoutResponse checkout = checkoutService.checkout(
                request("SEPAY", fingerprint), firstUser.getEmail(), "duplicate-ipn-race");
        SePayIpnRequest ipn = paidIpn(checkout.orderCode(), checkout.finalAmount());
        repositoryContentionGate.armOrder(checkout.orderCode());

        raceVoid(() -> sePayService.handleIpn(ipn), () -> sePayService.handleIpn(ipn));

        Order order = orderRepository.findById(checkout.orderId()).orElseThrow();
        Payment payment = paymentRepository.findByOrderId(checkout.orderId()).orElseThrow();
        assertEquals("PENDING", order.getStatus());
        assertEquals("PAID", order.getPaymentStatus());
        assertThat(order.getResourcesReleasedAt()).isNull();
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(ipn.transaction().transaction_id(), payment.getTransactionCode());
        assertThat(payment.getPaidAt()).isNotNull();

        assertEquals(1, orderRepository.count());
        assertEquals(1, orderItemRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals(1, transactionRepository.count());
        assertEquals(PaymentTransactionStatus.SUCCESS,
                transactionRepository.findAll().getFirst().getStatus());
        assertEquals(1, allocationRepository.count());
        assertEquals(SaleAllocationStatus.CONFIRMED,
                allocationRepository.findAll().getFirst().getStatus());
        assertEquals(1, usageRepository.count());
        assertEquals(1, inventoryLogRepository.count());
        assertEquals(1, variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity());

        SaleCampaignItem reloadedItem = campaignItemRepository.findById(saleItem.getId()).orElseThrow();
        assertEquals(0, reloadedItem.getReservedQuantity());
        assertEquals(1, reloadedItem.getSoldQuantity());
        SaleCustomerUsage usage = usageRepository
                .findByCampaignItemIdAndUserId(saleItem.getId(), firstUser.getId()).orElseThrow();
        assertEquals(0, usage.getReservedQuantity());
        assertEquals(1, usage.getPurchasedQuantity());
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Flash User");
        user.setPassword("password123");
        user.setBirthDate(LocalDate.of(1995, 1, 1));
        user.setGender(UserGender.OTHER);
        return userRepository.save(user);
    }

    private ProductVariant createVariant(String sku, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPrice(new BigDecimal("100.00"));
        variant.setStockQuantity(stock);
        variant.setStatus("ACTIVE");
        return variantRepository.save(variant);
    }

    private SaleCampaignItem createFlash(ProductVariant variant, int quota, int maxPerCustomer) {
        SaleCampaign campaign = new SaleCampaign();
        campaign.setCode("FLASH-" + UUID.randomUUID().toString().substring(0, 8));
        campaign.setName("Flash integration test");
        campaign.setType(SaleCampaignType.FLASH);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        SaleCampaignItem item = new SaleCampaignItem();
        item.setVariant(variant);
        item.setReferencePrice(variant.getPrice());
        item.setPromotionalPrice(new BigDecimal("80.00"));
        item.setQuota(quota);
        item.setMaxPerCustomer(maxPerCustomer);
        campaign.replaceItems(List.of(item));
        campaignRepository.saveAndFlush(campaign);
        return item;
    }

    private CreateSaleCampaignRequest campaignRequest(String code, ProductVariant variant) {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return new CreateSaleCampaignRequest(
                code,
                "Campaign code normalization test",
                null,
                null,
                SaleCampaignType.FLASH,
                startsAt,
                startsAt.plusSeconds(3600),
                List.of(new SaleCampaignItemRequest(
                        variant.getId(), new BigDecimal("80.00"), 5, 1)));
    }

    private UpdateSaleCampaignRequest updateCampaignRequest(long version, String code, ProductVariant variant) {
        CreateSaleCampaignRequest request = campaignRequest(code, variant);
        return new UpdateSaleCampaignRequest(
                version,
                request.code(),
                request.name(),
                request.description(),
                request.bannerUrl(),
                request.type(),
                request.startsAt(),
                request.endsAt(),
                request.items());
    }

    private Cart addCartItem(User user, ProductVariant variant, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(user);
            return cartRepository.save(created);
        });
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setVariantId(variant.getId());
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return cart;
    }

    private CheckoutRequest request(String method, String fingerprint) {
        return new CheckoutRequest(
                "Receiver", "0123456789", "Address", method,
                BigDecimal.ZERO, null, fingerprint);
    }

    private List<Result> race(Callable<CheckoutResponse> first, Callable<CheckoutResponse> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> one = executor.submit(() -> run(ready, start, first));
            Future<Result> two = executor.submit(() -> run(ready, start, second));
            awaitReady(ready);
            start.countDown();
            return List.of(
                    one.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    two.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Result run(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<CheckoutResponse> task) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return new Result(true, task.call(), null);
        } catch (CodedBusinessException exception) {
            return new Result(false, null, exception.getCode());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void raceVoid(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> runVoid(ready, start, first));
            Future<?> two = executor.submit(() -> runVoid(ready, start, second));
            awaitReady(ready);
            start.countDown();
            one.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            two.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> List<T> raceValues(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> one = executor.submit(() -> runValue(ready, start, first));
            Future<T> two = executor.submit(() -> runValue(ready, start, second));
            awaitReady(ready);
            start.countDown();
            return List.of(
                    one.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    two.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> T runValue(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<T> task) throws Exception {
        ready.countDown();
        start.await();
        return task.call();
    }

    private void runVoid(CountDownLatch ready, CountDownLatch start, ThrowingRunnable runnable) {
        try {
            ready.countDown();
            start.await();
            runnable.run();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void awaitReady(CountDownLatch ready) throws InterruptedException {
        if (!ready.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Race workers did not become ready within the timeout");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContentionTestConfiguration {

        @Bean
        static RepositoryContentionGate repositoryContentionGate() {
            return new RepositoryContentionGate();
        }
    }

    static final class RepositoryContentionGate implements BeanPostProcessor, MethodInterceptor {

        private final AtomicReference<Gate> cartGate = new AtomicReference<>();
        private final AtomicReference<Gate> orderGate = new AtomicReference<>();

        void armCart(Long userId) {
            cartGate.set(new Gate(
                    userId,
                    "Both checkouts did not reach the cart pessimistic-lock query"));
        }

        void armOrder(String orderCode) {
            orderGate.set(new Gate(
                    orderCode,
                    "Both IPNs did not reach the order pessimistic-lock query"));
        }

        void clear() {
            cartGate.set(null);
            orderGate.set(null);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof CartRepository) && !(bean instanceof OrderRepository)) {
                return bean;
            }
            ProxyFactory proxyFactory = new ProxyFactory(bean);
            proxyFactory.addAdvice(this);
            return proxyFactory.getProxy(bean.getClass().getClassLoader());
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if ("findWithLockByUserId".equals(invocation.getMethod().getName())) {
                awaitGate(cartGate, invocation.getArguments()[0]);
            } else if ("findWithLockByOrderCode".equals(invocation.getMethod().getName())) {
                awaitGate(orderGate, invocation.getArguments()[0]);
            }
            return invocation.proceed();
        }

        private void awaitGate(AtomicReference<Gate> reference, Object key) {
            Gate gate = reference.get();
            if (gate == null || !Objects.equals(gate.key(), key)) {
                return;
            }

            gate.contenders().countDown();
            try {
                if (!gate.contenders().await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError(gate.timeoutMessage());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while synchronizing race contenders", exception);
            } finally {
                if (gate.contenders().getCount() == 0) {
                    reference.compareAndSet(gate, null);
                }
            }
        }

        private record Gate(Object key, String timeoutMessage, CountDownLatch contenders) {

            private Gate(Object key, String timeoutMessage) {
                this(key, timeoutMessage, new CountDownLatch(2));
            }
        }
    }

    private SePayIpnRequest paidIpn(String orderCode, BigDecimal amount) {
        return new SePayIpnRequest(
                Instant.now().getEpochSecond(),
                "ORDER_PAID",
                new SePayIpnRequest.OrderData(
                        "ORDER-ID", "SEPAY-ORDER", "CAPTURED", "VND", amount,
                        orderCode, null, "Thanh toan"),
                new SePayIpnRequest.TransactionData(
                        "TRANSACTION-ID", "BANK_TRANSFER", "TX-LATE-" + UUID.randomUUID(), "PAYMENT",
                        "2026-07-15 00:00:00", "APPROVED", amount, "VND",
                        "AUTHENTICATION_SUCCESSFUL"));
    }

    private record Result(boolean success, CheckoutResponse response, String errorCode) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
