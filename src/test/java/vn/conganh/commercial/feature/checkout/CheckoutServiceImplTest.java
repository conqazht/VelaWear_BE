package vn.conganh.commercial.feature.checkout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.CouponNotValidException;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.EmptyCartException;
import vn.conganh.commercial.exception.InsufficientStockException;
import vn.conganh.commercial.exception.InvalidOrderTransitionException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.cart.CartItemRepository;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewResponse;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderStatusHistoryRepository;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.sepay.SePayService;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCustomerUsageRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponUsageRepository couponUsageRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SePayService sePayService;
    @Mock
    private InventoryLogRepository inventoryLogRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VariantPricingService pricingService;
    @Mock
    private SaleCampaignItemRepository campaignItemRepository;
    @Mock
    private SaleCampaignRepository campaignRepository;
    @Mock
    private SaleCustomerUsageRepository customerUsageRepository;
    @Mock
    private SaleAllocationRepository allocationRepository;
    @Mock
    private OrderResourceLifecycleService lifecycleService;
    @Mock
    private CatalogContentLocalizationService localizationService;

    @InjectMocks
    private CheckoutServiceImpl checkoutService;

    private User user;
    private Product product;
    private ProductVariant variant;
    private Cart cart;
    private CartItem cartItem;
    private CheckoutRequest request;
    private Order order;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("test@example.com");

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Test Product");
        product.setSlug("test-product");
        product.setStatus("ACTIVE");

        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 1L);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(100));
        variant.setStockQuantity(10);
        variant.setStatus("ACTIVE");
        variant.setSku("SKU-1");

        cart = new Cart();
        ReflectionTestUtils.setField(cart, "id", 300L);
        cart.setUser(user);

        cartItem = new CartItem();
        ReflectionTestUtils.setField(cartItem, "id", 400L);
        cartItem.setCart(cart);
        cartItem.setVariantId(1L);
        cartItem.setQuantity(2);

        request = new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                BigDecimal.valueOf(15),
                null,
                "pending-preview"
        );
        
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setUser(user);
        order.setStatus("PENDING");
        order.setPaymentStatus("UNPAID");
        ReflectionTestUtils.setField(checkoutService, "configuredShippingFee", BigDecimal.valueOf(15));
    }

    private void stubCartItems() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(eq(1L), anyString()))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem));
        when(pricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                .thenReturn(java.util.Map.of(1L, new VariantPricing(
                        1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                        PriceSource.BASE, null, null, null, 10)));
    }

    private CheckoutRequest requestWithFingerprint(String paymentMethod, String couponCode) {
        String fingerprint = checkoutService.preview(
                new CheckoutPreviewRequest(paymentMethod, couponCode), user.getEmail()).pricingFingerprint();
        return new CheckoutRequest(
                "Receiver",
                "0123456789",
                "Address",
                paymentMethod,
                BigDecimal.valueOf(15),
                couponCode,
                fingerprint);
    }

    private SaleCampaign liveCampaign(Long id) {
        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", id);
        campaign.setCode("SALE");
        campaign.setName("Khuyến mãi");
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(3600));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        return campaign;
    }

    private SaleCampaignItem campaignItem(SaleCampaign campaign) {
        SaleCampaignItem item = new SaleCampaignItem();
        ReflectionTestUtils.setField(item, "id", 3L);
        item.setCampaign(campaign);
        item.setVariant(variant);
        return item;
    }

    @Test
    void preview_englishLocale_localizesProductNameAndSlug() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem));
        when(pricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                .thenReturn(Map.of(1L, new VariantPricing(
                        1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                        PriceSource.BASE, null, null, null, 10)));
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        when(localizationService.localizeProducts(any(), eq("en"))).thenReturn(Map.of(
                1L,
                new CatalogContentLocalizationService.LocalizedProduct("English product", "english-product")));
        when(localizationService.localizeCampaignNames(any(), eq("en"))).thenReturn(Map.of());

        CheckoutPreviewResponse response = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null),
                "test@example.com",
                "en");

        assertEquals("English product", response.items().getFirst().productName());
        assertEquals("english-product", response.items().getFirst().productSlug());
    }

    @Test
    void checkout_priceConflict_containsLocalizedPreview() {
        SaleCampaign campaign = liveCampaign(2L);
        SaleCampaignItem campaignItem = campaignItem(campaign);
        VariantPricing pricing = new VariantPricing(
                1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                PriceSource.STANDARD_SALE, campaignItem, null, null, 10);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(1L, "key"))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem));
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        when(pricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                .thenReturn(Map.of(1L, pricing));
        when(localizationService.localizeProducts(any(), eq("en"))).thenReturn(Map.of(
                1L,
                new CatalogContentLocalizationService.LocalizedProduct("English product", "english-product")));
        when(localizationService.localizeCampaignNames(any(), eq("en")))
                .thenReturn(Map.of(2L, "English sale"));
        CheckoutRequest staleRequest = new CheckoutRequest(
                "Receiver", "0123456789", "Address", "COD",
                BigDecimal.valueOf(15), null, "stale");

        CodedBusinessException error = assertThrows(
                CodedBusinessException.class,
                () -> checkoutService.checkout(staleRequest, "test@example.com", "key", "en"));

        assertEquals("PRICE_CHANGED", error.getCode());
        CheckoutPreviewResponse preview = (CheckoutPreviewResponse) error.getDetails().get("preview");
        assertEquals("English product", preview.items().getFirst().productName());
        assertEquals("English sale", preview.items().getFirst().saleCampaignName());
        assertEquals("English sale", preview.items().getFirst().pricing().campaignName());
    }

    @Test
    void checkout_englishLocale_persistsLocalizedProductAndCampaignSnapshots() {
        SaleCampaign campaign = liveCampaign(2L);
        SaleCampaignItem campaignItem = campaignItem(campaign);
        VariantPricing pricing = new VariantPricing(
                1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                PriceSource.STANDARD_SALE, campaignItem, null, null, 10);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(1L, "key"))
                .thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem));
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        when(pricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                .thenReturn(Map.of(1L, pricing));
        when(localizationService.localizeProducts(any(), eq("en"))).thenReturn(Map.of(
                1L,
                new CatalogContentLocalizationService.LocalizedProduct("English product", "english-product")));
        when(localizationService.localizeCampaignNames(any(), eq("en")))
                .thenReturn(Map.of(2L, "English sale"));
        when(campaignRepository.findAllStatesWithLockByIdIn(List.of(2L))).thenReturn(List.of(campaign));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        when(productVariantRepository.decrementStock(1L, 2)).thenReturn(1);
        java.util.concurrent.atomic.AtomicReference<List<OrderItem>> snapshots =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<OrderItem> items = invocation.getArgument(0);
            ReflectionTestUtils.setField(items.getFirst(), "id", 200L);
            snapshots.set(items);
            return items;
        });
        when(lifecycleService.confirmLockedOrder(any(Order.class))).thenReturn(true);
        CheckoutPreviewResponse preview = checkoutService.preview(
                new CheckoutPreviewRequest("COD", null),
                "test@example.com",
                "en");
        CheckoutRequest localizedRequest = new CheckoutRequest(
                "Receiver", "0123456789", "Address", "COD",
                BigDecimal.valueOf(15), null, preview.pricingFingerprint());

        CheckoutResponse response = checkoutService.checkout(
                localizedRequest,
                "test@example.com",
                "key",
                "en");

        assertEquals("English product", snapshots.get().getFirst().getProductName());
        assertEquals("english-product", snapshots.get().getFirst().getProductSlug());
        assertEquals("English sale", snapshots.get().getFirst().getSaleCampaignName());
        assertEquals("English product", response.items().getFirst().productName());
        assertEquals("english-product", response.items().getFirst().productSlug());
        assertEquals("English sale", response.items().getFirst().saleCampaignName());
    }

    // Task 5.1
    @Test
    @DisplayName("Should successfully process checkout without coupon")
    void checkout_validRequest_createsOrder() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        stubCartItems();
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        request = requestWithFingerprint("COD", null);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 100L);
            return o;
        });
        when(productVariantRepository.decrementStock(1L, 2)).thenReturn(1);
        
        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "id", 200L);
        orderItem.setVariantId(1L);
        orderItem.setQuantity(2);
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<OrderItem> items = invocation.getArgument(0);
            ReflectionTestUtils.setField(items.getFirst(), "id", 200L);
            return items;
        });
        when(lifecycleService.confirmLockedOrder(any(Order.class))).thenReturn(true);
        
        CheckoutResponse response = checkoutService.checkout(request, "test@example.com");

        assertNotNull(response);
        assertEquals(100L, response.orderId());
        // 2 * 80 (campaign effective price) = 160
        assertEquals(BigDecimal.valueOf(160), response.subtotal());
        assertEquals(BigDecimal.valueOf(15), response.shippingFee());
        assertEquals(BigDecimal.valueOf(175), response.finalAmount());
        assertEquals(BigDecimal.ZERO, response.discountAmount());
        
        verify(orderRepository).save(any(Order.class));
        verify(productVariantRepository).decrementStock(1L, 2);
        verify(orderItemRepository).saveAll(any());
        verify(inventoryLogRepository).save(any());
        verify(cartItemRepository).deleteByCartId(300L);
        verify(paymentRepository, never()).save(any()); // COD payment
    }

    // Task 5.2
    @Test
    @DisplayName("Should throw InsufficientStockException when decrementStock returns 0")
    void checkout_insufficientStock_throwsException() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        stubCartItems();
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        request = requestWithFingerprint("COD", null);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productVariantRepository.decrementStock(1L, 2)).thenReturn(0);

        CodedBusinessException error = assertThrows(
                CodedBusinessException.class,
                () -> checkoutService.checkout(request, "test@example.com"));
        assertEquals("INSUFFICIENT_STOCK", error.getCode());
        
        verify(productVariantRepository).decrementStock(1L, 2);
        verify(orderItemRepository, never()).saveAll(any()); // Should stop before saving items
    }

    // Task 5.3
    @Test
    @DisplayName("Should throw CouponNotValidException when consumeUsage returns 0")
    void checkout_couponConsumeFails_throwsException() {
        Coupon coupon = new Coupon();
        ReflectionTestUtils.setField(coupon, "id", 10L);
        coupon.setCode("DISCOUNT10");
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(BigDecimal.valueOf(10));
        coupon.setMinOrderAmount(BigDecimal.valueOf(50));
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartDate(Instant.now().minusSeconds(60));
        coupon.setEndDate(Instant.now().plusSeconds(3600));
        
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        stubCartItems();
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
        when(couponRepository.findByCode("DISCOUNT10")).thenReturn(Optional.of(coupon));
        CheckoutRequest requestWithCoupon = requestWithFingerprint("COD", "DISCOUNT10");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productVariantRepository.decrementStock(1L, 2)).thenReturn(1);

        when(couponRepository.consumeUsage(eq(10L), any(Instant.class))).thenReturn(0); // Failed to consume

        assertThrows(CouponNotValidException.class, () -> checkoutService.checkout(requestWithCoupon, "test@example.com"));
        
        verify(couponRepository).consumeUsage(eq(10L), any(Instant.class));
        verify(couponUsageRepository, never()).save(any());
        verify(productVariantRepository).decrementStock(1L, 2);
    }

    // Task 5.6
    @Test
    @DisplayName("Should successfully cancel order, restore stock, and release coupon")
    void cancelOrder_validOrder_restoresStockAndReleasesCoupon() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(lifecycleService.findLocked(1L)).thenReturn(order);
        
        checkoutService.cancelOrder(1L, "test@example.com");

        verify(lifecycleService).releaseLockedOrder(order, "FAILED", "CUSTOMER_CANCELLED");
    }
    
    @Test
    @DisplayName("Should do nothing if cancelling an already cancelled order")
    void cancelOrder_alreadyCancelled_returnsWithoutAction() {
        order.setStatus("CANCELLED");
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(lifecycleService.findLocked(1L)).thenReturn(order);

        checkoutService.cancelOrder(1L, "test@example.com");

        verify(lifecycleService, never()).releaseLockedOrder(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject cancellation after payment succeeds")
    void cancelOrder_paidOrder_throwsException() {
        order.setPaymentStatus("PAID");
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
        when(lifecycleService.findLocked(1L)).thenReturn(order);

        assertThrows(InvalidRequestException.class,
                () -> checkoutService.cancelOrder(1L, "test@example.com"));

        verify(lifecycleService, never()).releaseLockedOrder(any(), anyString(), anyString());
    }

    // ─── Image selection characterization (BE-006) ──────────────────────────

    @Nested
    @DisplayName("Image selection characterization")
    class ImageSelectionCharacterizationTest {

        @Captor
        private ArgumentCaptor<List<Long>> productIdCaptor;

        private ProductImage makeImage(Long id, Product product, ProductVariant variant,
                                       Boolean isThumbnail, Integer sortOrder, String url) {
            ProductImage image = new ProductImage();
            ReflectionTestUtils.setField(image, "id", id);
            image.setProduct(product);
            image.setVariant(variant);
            image.setIsThumbnail(isThumbnail);
            image.setSortOrder(sortOrder);
            image.setImage(url);
            return image;
        }

        private void stubCheckoutInfra() {
            when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
            when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
            when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
            when(orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(eq(1L), anyString()))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem));
            when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(variant));
            when(pricingService.resolve(eq(List.of(variant)), any(Instant.class), eq(1L)))
                    .thenReturn(Map.of(1L, new VariantPricing(
                            1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                            PriceSource.BASE, null, null, null, 10)));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 100L);
                return o;
            });
            when(productVariantRepository.decrementStock(1L, 2)).thenReturn(1);
            when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<OrderItem> items = invocation.getArgument(0);
                ReflectionTestUtils.setField(items.getFirst(), "id", 200L);
                return items;
            });
            when(lifecycleService.confirmLockedOrder(any(Order.class))).thenReturn(true);
        }

        private CheckoutResponse doCheckout() {
            CheckoutPreviewResponse preview = checkoutService.preview(
                    new CheckoutPreviewRequest("COD", null), "test@example.com");
            CheckoutRequest req = new CheckoutRequest(
                    "Receiver", "0123456789", "Address", "COD",
                    BigDecimal.valueOf(15), null, preview.pricingFingerprint());
            return checkoutService.checkout(req, "test@example.com");
        }

        @Test
        @DisplayName("Should prefer variant-specific image over product-level fallback")
        void checkout_variantImagePreferredOverProductFallback() {
            stubCheckoutInfra();
            ProductImage productImage = makeImage(10L, product, null, true, 0, "product-thumb.jpg");
            ProductImage variantImage = makeImage(20L, product, variant, false, 0, "variant.jpg");
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of(productImage, variantImage));

            CheckoutResponse response = doCheckout();

            assertEquals("variant.jpg", response.items().getFirst().image());
        }

        @Test
        @DisplayName("Should fall back to product image when variant has no specific image")
        void checkout_productFallbackWhenNoVariantImage() {
            stubCheckoutInfra();
            ProductImage productImage = makeImage(10L, product, null, true, 0, "product-thumb.jpg");
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of(productImage));

            CheckoutResponse response = doCheckout();

            assertEquals("product-thumb.jpg", response.items().getFirst().image());
        }

        @Test
        @DisplayName("Should prefer thumbnail image within variant images")
        void checkout_thumbnailPreferredWithinVariantImages() {
            stubCheckoutInfra();
            ProductImage nonThumb = makeImage(10L, product, variant, false, 0, "non-thumb.jpg");
            ProductImage thumb = makeImage(20L, product, variant, true, 5, "thumb.jpg");
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of(nonThumb, thumb));

            CheckoutResponse response = doCheckout();

            assertEquals("thumb.jpg", response.items().getFirst().image());
        }

        @Test
        @DisplayName("Should use sortOrder then ID as tie-breaker when thumbnail is equal")
        void checkout_sortOrderAndIdTieBreaker() {
            stubCheckoutInfra();
            ProductImage img1 = makeImage(30L, product, variant, false, 2, "sort2-id30.jpg");
            ProductImage img2 = makeImage(10L, product, variant, false, 2, "sort2-id10.jpg");
            ProductImage img3 = makeImage(20L, product, variant, false, 1, "sort1-id20.jpg");
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of(img1, img2, img3));

            CheckoutResponse response = doCheckout();

            // sortOrder=1 wins, then within sortOrder=1, ID=20 is the only one
            assertEquals("sort1-id20.jpg", response.items().getFirst().image());
        }

        @Test
        @DisplayName("Should return null image when no images exist")
        void checkout_noImages_returnsNull() {
            stubCheckoutInfra();
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of());

            CheckoutResponse response = doCheckout();

            assertNull(response.items().getFirst().image());
        }

        @Test
        @DisplayName("Should call findByProductIdIn exactly once regardless of line count")
        void checkout_bulkImageCall_exactlyOnce() {
            stubCheckoutInfra();
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of());

            doCheckout();

            verify(productImageRepository, times(1)).findByProductIdIn(any());
            verify(productImageRepository, never()).findByVariantId(anyLong());
            verify(productImageRepository, never()).findByProductId(anyLong());
        }

        @Test
        @DisplayName("Should handle duplicate product lines with single bulk image call")
        void checkout_duplicateProductLines_singleBulkCall() {
            // Set up two variants of the same product
            ProductVariant variant2 = new ProductVariant();
            ReflectionTestUtils.setField(variant2, "id", 2L);
            variant2.setProduct(product);
            variant2.setPrice(BigDecimal.valueOf(50));
            variant2.setStockQuantity(5);
            variant2.setStatus("ACTIVE");
            variant2.setSku("SKU-2");

            CartItem cartItem2 = new CartItem();
            ReflectionTestUtils.setField(cartItem2, "id", 401L);
            cartItem2.setCart(cart);
            cartItem2.setVariantId(2L);
            cartItem2.setQuantity(1);

            when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(user));
            when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
            when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
            when(orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(eq(1L), anyString()))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.findByCartId(300L)).thenReturn(List.of(cartItem, cartItem2));
            when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(any()))
                    .thenReturn(List.of(variant, variant2));
            when(pricingService.resolve(any(), any(Instant.class), eq(1L)))
                    .thenReturn(Map.of(
                            1L, new VariantPricing(1L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                                    PriceSource.BASE, null, null, null, 10),
                            2L, new VariantPricing(2L, BigDecimal.valueOf(50), BigDecimal.valueOf(50),
                                    PriceSource.BASE, null, null, null, 5)));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                ReflectionTestUtils.setField(o, "id", 100L);
                return o;
            });
            when(productVariantRepository.decrementStock(1L, 2)).thenReturn(1);
            when(productVariantRepository.decrementStock(2L, 1)).thenReturn(1);
            when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<OrderItem> items = invocation.getArgument(0);
                long id = 200L;
                for (OrderItem item : items) {
                    ReflectionTestUtils.setField(item, "id", id++);
                }
                return items;
            });
            when(lifecycleService.confirmLockedOrder(any(Order.class))).thenReturn(true);

            ProductImage v1Image = makeImage(10L, product, variant, true, 0, "v1-thumb.jpg");
            ProductImage v2Image = makeImage(20L, product, variant2, false, 0, "v2.jpg");
            when(productImageRepository.findByProductIdIn(any())).thenReturn(List.of(v1Image, v2Image));

            CheckoutPreviewResponse preview = checkoutService.preview(
                    new CheckoutPreviewRequest("COD", null), "test@example.com");
            CheckoutRequest req = new CheckoutRequest(
                    "Receiver", "0123456789", "Address", "COD",
                    BigDecimal.valueOf(15), null, preview.pricingFingerprint());
            CheckoutResponse response = checkoutService.checkout(req, "test@example.com");

            // Only one bulk call for images even with 2 variants of the same product
            verify(productImageRepository, times(1)).findByProductIdIn(productIdCaptor.capture());
            List<Long> capturedIds = productIdCaptor.getValue();
            assertEquals(1, capturedIds.size(), "Should deduplicate product IDs");
            assertEquals(product.getId(), capturedIds.getFirst());
            verify(productImageRepository, never()).findByVariantId(anyLong());
            verify(productImageRepository, never()).findByProductId(anyLong());
        }
    }
}
