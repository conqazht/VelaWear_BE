package vn.conganh.commercial.feature.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.CouponNotValidException;
import vn.conganh.commercial.exception.EmptyCartException;
import vn.conganh.commercial.exception.InvalidOrderTransitionException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.cart.CartItemRepository;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.checkout.dto.CheckoutItemResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewItemResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutPreviewResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.checkout.dto.PaymentInitiationResponse;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsage;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.sepay.SePayCheckoutForm;
import vn.conganh.commercial.feature.payment.sepay.SePayService;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLog;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.SaleAllocation;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleAllocationStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCustomerUsageRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@Slf4j
@RequiredArgsConstructor
@Service
public class CheckoutServiceImpl implements CheckoutService {

    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);
    private static final Duration PAYMENT_GRACE = Duration.ofSeconds(30);

    /**
     * Deterministic image selection: thumbnail first, then lowest sortOrder, then lowest ID.
     * Shared between bulk prefetch selection and any standalone selection call.
     */
    static final Comparator<ProductImage> IMAGE_SELECTION_ORDER = Comparator
            .comparing((ProductImage image) -> !Boolean.TRUE.equals(image.getIsThumbnail()))
            .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo));

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final SePayService sePayService;
    private final VariantPricingService pricingService;
    private final SaleCampaignItemRepository campaignItemRepository;
    private final SaleCampaignRepository campaignRepository;
    private final SaleCustomerUsageRepository customerUsageRepository;
    private final SaleAllocationRepository allocationRepository;
    private final OrderResourceLifecycleService lifecycleService;
    private final CatalogContentLocalizationService localizationService;

    @Value("${app.checkout.shipping-fee:30000}")
    private BigDecimal configuredShippingFee;

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(CheckoutPreviewRequest request, String userEmail) {
        return preview(request, userEmail, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(
            CheckoutPreviewRequest request,
            String userEmail,
            String localeCode) {
        User user = findUser(userEmail);
        validatePaymentMethod(request.paymentMethod());
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow(EmptyCartException::new);
        Quote quote = buildQuote(user, cart, request.couponCode(), Instant.now());
        return toPreviewResponse(quote, localizeQuote(quote, localeCode));
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request, String userEmail) {
        return checkout(
                request,
                userEmail,
                UUID.randomUUID().toString(),
                CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request, String userEmail, String idempotencyKey) {
        return checkout(request, userEmail, idempotencyKey, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(
            CheckoutRequest request,
            String userEmail,
            String idempotencyKey,
            String localeCode) {
        User user = findUser(userEmail);
        validatePaymentMethod(request.paymentMethod());
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(request);

        Cart cart = cartRepository.findWithLockByUserId(user.getId()).orElseThrow(EmptyCartException::new);
        Optional<Order> existing = orderRepository.findWithLockByUserIdAndCheckoutIdempotencyKey(
                user.getId(), normalizedKey);
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getCheckoutRequestHash())) {
                throw conflict("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with a different checkout request", Map.of());
            }
            return existingResponse(existing.get());
        }

        Instant now = Instant.now();
        Quote quote = buildQuote(user, cart, request.couponCode(), now);
        QuoteLocalization localization = localizeQuote(quote, localeCode);
        if (!java.util.Objects.equals(request.pricingFingerprint(), quote.pricingFingerprint())) {
            throw conflict("PRICE_CHANGED", "Cart price changed after preview",
                    Map.of(
                            "pricingFingerprint", quote.pricingFingerprint(),
                            "preview", toPreviewResponse(quote, localization)));
        }

        lockAndRecheckStandardCampaigns(quote.lines());

        boolean online = !"COD".equalsIgnoreCase(request.paymentMethod());
        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(newOrderCode());
        order.setStatus("PENDING");
        order.setSubtotal(quote.subtotal());
        order.setShippingFee(quote.shippingFee());
        order.setDiscountAmount(quote.discountAmount());
        order.setFinalAmount(quote.finalAmount());
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        order.setPaymentMethod(request.paymentMethod().toUpperCase());
        order.setPaymentStatus("UNPAID");
        order.setCheckoutIdempotencyKey(normalizedKey);
        order.setCheckoutRequestHash(requestHash);
        if (online) {
            order.setPaymentDueAt(now.plus(PAYMENT_WINDOW));
            order.setReservationExpiresAt(now.plus(PAYMENT_WINDOW).plus(PAYMENT_GRACE));
        }
        order = orderRepository.save(order);

        reserveFlashAndStock(quote.lines(), user, now);
        consumeCoupon(quote, user, order, now);

        List<OrderItem> orderItems = createOrderItems(quote.lines(), order, localization);
        createAllocations(quote.lines(), orderItems, user, online);

        for (OrderItem orderItem : orderItems) {
            writeInventoryLog(quote.variantMap().get(orderItem.getVariantId()),
                    -orderItem.getQuantity(), "ORDER", "ORDER_ITEM", orderItem.getId(), null);
        }

        if (!online) {
            lifecycleService.confirmLockedOrder(order);
        }

        Long paymentId = null;
        PaymentInitiationResponse paymentInitiation = null;
        if (online) {
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setProvider(PaymentProvider.SEPAY);
            payment.setAmount(order.getFinalAmount());
            payment.setStatus(PaymentStatus.PENDING);
            payment = paymentRepository.save(payment);
            paymentId = payment.getId();

            SePayCheckoutForm checkoutForm = sePayService.createCheckoutForm(order);
            paymentInitiation = new PaymentInitiationResponse(
                    PaymentProvider.SEPAY.name(), "POST", checkoutForm.actionUrl(), checkoutForm.fields());
        }

        cartItemRepository.deleteByCartId(cart.getId());
        return CheckoutResponse.fromEntity(
                order,
                orderItems.stream().map(CheckoutItemResponse::fromEntity).toList(),
                paymentId,
                paymentInitiation);
    }

    @Override
    @Transactional
    public void cancelOrder(long orderId, String userEmail) {
        User user = findUser(userEmail);
        Order order = lifecycleService.findLocked(orderId);
        if (!order.getUser().getId().equals(user.getId())) {
            throw new InvalidRequestException("Order does not belong to user");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }
        if ("PAID".equals(order.getPaymentStatus())) {
            throw new InvalidRequestException("A paid order cannot be cancelled");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new InvalidOrderTransitionException(order.getStatus(), "CANCELLED");
        }
        lifecycleService.releaseLockedOrder(order, "FAILED", "CUSTOMER_CANCELLED");
        paymentRepository.findWithLockByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });
    }

    @Override
    @Transactional
    public void handlePaymentFailure(long orderId) {
        Order order = lifecycleService.findLocked(orderId);
        if (order.getResourcesReleasedAt() != null || "CANCELLED".equals(order.getStatus())) {
            return;
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new InvalidOrderTransitionException(order.getStatus(), "CANCELLED");
        }
        lifecycleService.releaseLockedOrder(order, "FAILED", "PAYMENT_FAILED");
        paymentRepository.findWithLockByOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        });
    }

    private Quote buildQuote(User user, Cart cart, String couponCode, Instant now) {
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId()).stream()
                .sorted(Comparator.comparing(CartItem::getVariantId))
                .toList();
        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }
        List<Long> variantIds = cartItems.stream().map(CartItem::getVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllByIdInAndDeletedAtIsNull(variantIds);
        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variantMap.size() != variantIds.size()) {
            throw new InvalidRequestException("One or more product variants are unavailable");
        }
        for (ProductVariant variant : variants) {
            if (!"ACTIVE".equals(variant.getStatus())
                    || variant.getProduct().getDeletedAt() != null
                    || !"ACTIVE".equals(variant.getProduct().getStatus())) {
                throw new InvalidRequestException("Product variant not available: " + variant.getId());
            }
        }

        Map<Long, VariantPricing> pricing = pricingService.resolve(variants, now, user.getId());
        List<QuoteLine> lines = cartItems.stream()
                .map(item -> new QuoteLine(item, variantMap.get(item.getVariantId()), pricing.get(item.getVariantId())))
                .toList();
        BigDecimal subtotal = lines.stream()
                .map(line -> line.pricing().effectivePrice().multiply(BigDecimal.valueOf(line.item().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eligibleSubtotal = lines.stream()
                .filter(line -> line.pricing().priceSource() != PriceSource.FLASH_SALE)
                .map(line -> line.pricing().effectivePrice().multiply(BigDecimal.valueOf(line.item().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Coupon coupon = findAndValidateCoupon(couponCode, eligibleSubtotal, now);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO : calculateDiscount(coupon, eligibleSubtotal);
        BigDecimal shippingFee = configuredShippingFee == null ? BigDecimal.ZERO : configuredShippingFee;
        BigDecimal finalAmount = subtotal.add(shippingFee).subtract(discount).max(BigDecimal.ZERO);
        String fingerprint = pricingFingerprint(
                lines, coupon, eligibleSubtotal, shippingFee, discount, finalAmount);
        return new Quote(now, fingerprint, lines, variantMap, coupon,
                subtotal, eligibleSubtotal, shippingFee, discount, finalAmount);
    }

    private void reserveFlashAndStock(List<QuoteLine> lines, User user, Instant now) {
        for (QuoteLine line : lines) {
            VariantPricing pricing = line.pricing();
            int quantity = line.item().getQuantity();
            if (pricing.isFlash()) {
                SaleCampaignItem campaignItem = pricing.campaignItem();
                customerUsageRepository.createCounterIfAbsent(campaignItem.getId(), user.getId());
                int max = campaignItem.getMaxPerCustomer() == null
                        ? Integer.MAX_VALUE : campaignItem.getMaxPerCustomer();
                if (campaignItemRepository.reserveQuota(campaignItem.getId(), quantity, now) != 1) {
                    String code = !campaignItem.getCampaign().getEndsAt().isAfter(now)
                            ? "FLASH_SALE_ENDED" : "FLASH_SALE_SOLD_OUT";
                    throw conflict(code, "Flash sale quota is no longer available",
                            Map.of("variantId", line.variant().getId()));
                }
                if (customerUsageRepository.reserveWithinLimit(
                        campaignItem.getId(), user.getId(), quantity, max) != 1) {
                    throw conflict("FLASH_SALE_LIMIT_EXCEEDED", "Flash sale customer limit exceeded",
                            Map.of("variantId", line.variant().getId(), "maxPerCustomer", max));
                }
            }
            if (variantRepository.decrementStock(line.variant().getId(), quantity) != 1) {
                throw conflict("INSUFFICIENT_STOCK", "Product stock is no longer sufficient",
                        Map.of("variantId", line.variant().getId(), "sku", line.variant().getSku()));
            }
        }
    }

    private void lockAndRecheckStandardCampaigns(List<QuoteLine> lines) {
        List<Long> campaignIds = lines.stream()
                .filter(line -> line.pricing().priceSource() == PriceSource.STANDARD_SALE)
                .map(line -> line.pricing().campaignItem().getCampaign().getId())
                .distinct()
                .sorted()
                .toList();
        if (campaignIds.isEmpty()) {
            return;
        }
        Instant lockedAt = Instant.now();
        List<vn.conganh.commercial.feature.salecampaign.SaleCampaign> lockedCampaigns =
                campaignRepository.findAllStatesWithLockByIdIn(campaignIds);
        boolean stillLive = lockedCampaigns.size() == campaignIds.size()
                && lockedCampaigns.stream().allMatch(campaign ->
                        campaign.getStatus() == SaleCampaignStatus.PUBLISHED
                                && !lockedAt.isBefore(campaign.getStartsAt())
                                && lockedAt.isBefore(campaign.getEndsAt()));
        if (!stillLive) {
            throw conflict("PRICE_CHANGED", "A scheduled sale ended while checkout was being confirmed", Map.of());
        }
    }

    private void consumeCoupon(Quote quote, User user, Order order, Instant now) {
        if (quote.coupon() == null) {
            return;
        }
        if (couponRepository.consumeUsage(quote.coupon().getId(), now) != 1) {
            throw new CouponNotValidException("Coupon is no longer available or usage limit reached");
        }
        CouponUsage usage = new CouponUsage();
        usage.setCoupon(quote.coupon());
        usage.setUser(user);
        usage.setOrder(order);
        usage.setDiscountAmount(quote.discountAmount());
        couponUsageRepository.save(usage);
    }

    private List<OrderItem> createOrderItems(
            List<QuoteLine> lines,
            Order order,
            QuoteLocalization localization) {
        ImageIndex imageIndex = prefetchImageIndex(lines);
        List<OrderItem> orderItems = new ArrayList<>();
        for (QuoteLine line : lines) {
            ProductVariant variant = line.variant();
            VariantPricing pricing = line.pricing();
            CatalogContentLocalizationService.LocalizedProduct localizedProduct = localizedProduct(
                    line,
                    localization);
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariantId(variant.getId());
            orderItem.setProductName(localizedProduct.name());
            orderItem.setProductSlug(localizedProduct.slug());
            orderItem.setVariantName(buildVariantName(variant));
            orderItem.setSku(variant.getSku());
            orderItem.setImage(resolveItemImage(variant, imageIndex));
            orderItem.setListPrice(pricing.listPrice());
            orderItem.setPrice(pricing.effectivePrice());
            orderItem.setPriceSource(pricing.priceSource());
            orderItem.setSaleCampaignItem(pricing.campaignItem());
            if (pricing.campaignItem() != null) {
                orderItem.setSaleCampaignCode(pricing.campaignItem().getCampaign().getCode());
                orderItem.setSaleCampaignName(localizedCampaignName(pricing, localization));
            }
            orderItem.setQuantity(line.item().getQuantity());
            orderItem.setSubtotal(pricing.effectivePrice().multiply(BigDecimal.valueOf(line.item().getQuantity())));
            orderItem.setStatus("PENDING");
            orderItems.add(orderItem);
        }
        return orderItemRepository.saveAll(orderItems);
    }

    private void createAllocations(
            List<QuoteLine> lines,
            List<OrderItem> orderItems,
            User user,
            boolean online) {
        Map<Long, OrderItem> byVariant = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getVariantId, Function.identity()));
        List<SaleAllocation> allocations = lines.stream()
                .filter(line -> line.pricing().isFlash())
                .map(line -> {
                    SaleAllocation allocation = new SaleAllocation();
                    allocation.setCampaignItem(line.pricing().campaignItem());
                    allocation.setOrderItem(byVariant.get(line.variant().getId()));
                    allocation.setUser(user);
                    allocation.setQuantity(line.item().getQuantity());
                    allocation.setStatus(SaleAllocationStatus.RESERVED);
                    return allocation;
                })
                .toList();
        allocationRepository.saveAll(allocations);
        allocationRepository.flush();
    }

    private Coupon findAndValidateCoupon(String code, BigDecimal eligibleSubtotal, Instant now) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Coupon coupon = couponRepository.findByCode(code.trim())
                .orElseThrow(() -> new CouponNotValidException("Coupon not found"));
        if (coupon.getStatus() != CouponStatus.ACTIVE
                || coupon.getStartDate().isAfter(now)
                || coupon.getEndDate().isBefore(now)
                || (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit())) {
            throw new CouponNotValidException("Coupon is not active");
        }
        if (coupon.getMinOrderAmount() != null
                && eligibleSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new CouponNotValidException("Coupon-eligible subtotal does not meet minimum amount");
        }
        if (eligibleSubtotal.signum() == 0) {
            throw new CouponNotValidException("Coupon cannot be applied to Flash Sale items");
        }
        return coupon;
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal eligibleSubtotal) {
        BigDecimal discount;
        if (coupon.getType() == CouponType.PERCENTAGE) {
            discount = eligibleSubtotal.multiply(coupon.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        } else {
            discount = coupon.getValue();
        }
        return discount.min(eligibleSubtotal);
    }

    private CheckoutResponse existingResponse(Order order) {
        Instant now = Instant.now();
        if (order.getReservationExpiresAt() != null
                && !order.getReservationExpiresAt().isAfter(now)
                && order.getResourcesReleasedAt() == null
                && "PENDING".equals(order.getStatus())
                && "UNPAID".equals(order.getPaymentStatus())) {
            lifecycleService.releaseLockedOrder(order, "FAILED", "PAYMENT_TIMEOUT");
            paymentRepository.findWithLockByOrderId(order.getId()).ifPresent(expiredPayment -> {
                if (expiredPayment.getStatus() == PaymentStatus.PENDING) {
                    expiredPayment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(expiredPayment);
                }
            });
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        Long paymentId = payment == null ? null : payment.getId();
        PaymentInitiationResponse paymentInitiation = null;
        if (payment != null
                && payment.getStatus() == PaymentStatus.PENDING
                && "PENDING".equals(order.getStatus())
                && "UNPAID".equals(order.getPaymentStatus())
                && (order.getPaymentDueAt() == null || order.getPaymentDueAt().isAfter(now))
                && (order.getReservationExpiresAt() == null || order.getReservationExpiresAt().isAfter(now))) {
            SePayCheckoutForm checkoutForm = sePayService.createCheckoutForm(order);
            paymentInitiation = new PaymentInitiationResponse(
                    PaymentProvider.SEPAY.name(), "POST", checkoutForm.actionUrl(), checkoutForm.fields());
        }
        return CheckoutResponse.fromEntity(
                order, items.stream().map(CheckoutItemResponse::fromEntity).toList(), paymentId, paymentInitiation);
    }

    private String pricingFingerprint(
            List<QuoteLine> lines,
            Coupon coupon,
            BigDecimal eligibleSubtotal,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal finalAmount) {
        String canonical = lines.stream()
                .sorted(Comparator.comparing(line -> line.variant().getId()))
                .map(line -> String.join(":",
                        line.variant().getId().toString(),
                        Integer.toString(line.item().getQuantity()),
                        line.pricing().listPrice().toPlainString(),
                        line.pricing().effectivePrice().toPlainString(),
                        line.pricing().priceSource().name(),
                        line.pricing().campaignItem() == null ? "-" : line.pricing().campaignItem().getId().toString()))
                .collect(Collectors.joining("|"));
        String couponSnapshot = coupon == null
                ? "-"
                : String.join(":",
                        coupon.getId().toString(),
                        coupon.getCode(),
                        coupon.getType().name(),
                        coupon.getValue().toPlainString(),
                        coupon.getMaxDiscount() == null ? "-" : coupon.getMaxDiscount().toPlainString(),
                        coupon.getMinOrderAmount() == null ? "-" : coupon.getMinOrderAmount().toPlainString());
        return sha256(canonical
                + "|coupon=" + couponSnapshot
                + "|eligible=" + eligibleSubtotal.toPlainString()
                + "|shipping=" + shippingFee.toPlainString()
                + "|discount=" + discountAmount.toPlainString()
                + "|final=" + finalAmount.toPlainString());
    }

    private String requestHash(CheckoutRequest request) {
        return sha256(String.join("|",
                nullSafe(request.receiverName()),
                nullSafe(request.receiverPhone()),
                nullSafe(request.receiverAddress()),
                nullSafe(request.paymentMethod()).toUpperCase(),
                nullSafe(request.couponCode()),
                nullSafe(request.pricingFingerprint())));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private void validatePaymentMethod(String paymentMethod) {
        if (!"COD".equalsIgnoreCase(paymentMethod) && !"SEPAY".equalsIgnoreCase(paymentMethod)) {
            throw new InvalidRequestException("Only COD and SEPAY payment methods are supported");
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new CodedBusinessException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key header must not be blank",
                    HttpStatus.BAD_REQUEST);
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new CodedBusinessException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be at most 100 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String newOrderCode() {
        return "VELA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String buildVariantName(ProductVariant variant) {
        String colorName = variant.getColor() == null ? "" : variant.getColor().getName();
        String sizeName = variant.getSize() == null ? "" : variant.getSize().getName();
        if (!colorName.isEmpty() && !sizeName.isEmpty()) {
            return colorName + " / " + sizeName;
        }
        return colorName.isEmpty() ? sizeName : colorName;
    }

    /**
     * Prefetch all product images for the given checkout lines in a single bulk query,
     * then build in-memory indexes keyed by variant ID and product ID.
     */
    private ImageIndex prefetchImageIndex(List<QuoteLine> lines) {
        Set<Long> productIds = lines.stream()
                .map(line -> line.variant().getProduct().getId())
                .collect(Collectors.toSet());
        List<ProductImage> allImages = productIds.isEmpty()
                ? List.of()
                : productImageRepository.findByProductIdIn(new ArrayList<>(productIds));
        Map<Long, List<ProductImage>> byVariant = new HashMap<>();
        Map<Long, List<ProductImage>> byProduct = new HashMap<>();
        for (ProductImage image : allImages) {
            byProduct.computeIfAbsent(image.getProduct().getId(), k -> new ArrayList<>()).add(image);
            if (image.getVariant() != null) {
                byVariant.computeIfAbsent(image.getVariant().getId(), k -> new ArrayList<>()).add(image);
            }
        }
        return new ImageIndex(byVariant, byProduct);
    }

    /**
     * Resolve the best image for an order item using prefetched indexes.
     * Selection order: variant-specific images first, then product-level fallback.
     * Within each group: thumbnail first, lowest sortOrder, lowest ID.
     */
    private String resolveItemImage(ProductVariant variant, ImageIndex imageIndex) {
        Optional<String> variantImage = selectImage(
                imageIndex.byVariant().getOrDefault(variant.getId(), List.of()));
        return variantImage.orElseGet(() -> selectImage(
                imageIndex.byProduct().getOrDefault(variant.getProduct().getId(), List.of())).orElse(null));
    }

    private Optional<String> selectImage(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return Optional.empty();
        }
        return images.stream()
                .sorted(IMAGE_SELECTION_ORDER)
                .map(ProductImage::getImage)
                .findFirst();
    }

    private void writeInventoryLog(
            ProductVariant variant,
            int changeQuantity,
            String type,
            String referenceType,
            Long referenceId,
            String reason) {
        InventoryLog logRecord = new InventoryLog();
        logRecord.setVariant(variant);
        logRecord.setChangeQuantity(changeQuantity);
        logRecord.setType(type);
        logRecord.setReferenceType(referenceType);
        logRecord.setReferenceId(referenceId);
        logRecord.setReason(reason);
        inventoryLogRepository.save(logRecord);
    }

    private CodedBusinessException conflict(String code, String message, Map<String, Object> details) {
        return new CodedBusinessException(code, message, HttpStatus.CONFLICT, details);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private QuoteLocalization localizeQuote(Quote quote, String localeCode) {
        Map<Long, CatalogContentLocalizationService.LocalizedProduct> products =
                localizationService.localizeProducts(
                        quote.lines().stream().map(line -> line.variant().getProduct()).toList(),
                        localeCode);
        Map<Long, String> campaigns = localizationService.localizeCampaignNames(
                quote.lines().stream()
                        .map(QuoteLine::pricing)
                        .filter(pricing -> pricing.campaignItem() != null)
                        .map(pricing -> pricing.campaignItem().getCampaign())
                        .toList(),
                localeCode);
        return new QuoteLocalization(
                products == null ? Map.of() : products,
                campaigns == null ? Map.of() : campaigns);
    }

    private CatalogContentLocalizationService.LocalizedProduct localizedProduct(
            QuoteLine line,
            QuoteLocalization localization) {
        Product product = line.variant().getProduct();
        return localization.products().getOrDefault(
                product.getId(),
                new CatalogContentLocalizationService.LocalizedProduct(product.getName(), product.getSlug()));
    }

    private String localizedCampaignName(VariantPricing pricing, QuoteLocalization localization) {
        if (pricing.campaignItem() == null) {
            return null;
        }
        var campaign = pricing.campaignItem().getCampaign();
        return localization.campaignNames().getOrDefault(campaign.getId(), campaign.getName());
    }

    private CheckoutPreviewResponse toPreviewResponse(Quote quote, QuoteLocalization localization) {
        return new CheckoutPreviewResponse(
                quote.serverTime(),
                quote.pricingFingerprint(),
                quote.subtotal(),
                quote.eligibleSubtotal(),
                quote.shippingFee(),
                quote.discountAmount(),
                quote.finalAmount(),
                quote.lines().stream().map(line -> {
                    CatalogContentLocalizationService.LocalizedProduct product = localizedProduct(
                            line,
                            localization);
                    String campaignName = localizedCampaignName(line.pricing(), localization);
                    return new CheckoutPreviewItemResponse(
                            line.variant().getId(),
                            line.variant().getProduct().getId(),
                            product.name(),
                            product.slug(),
                            line.variant().getSku(),
                            line.item().getQuantity(),
                            line.pricing().listPrice(),
                            line.pricing().effectivePrice(),
                            line.pricing().effectivePrice().multiply(
                                    BigDecimal.valueOf(line.item().getQuantity())),
                            line.pricing().priceSource(),
                            line.pricing().campaignItem() == null
                                    ? null : line.pricing().campaignItem().getId(),
                            line.pricing().campaignItem() == null
                                    ? null : line.pricing().campaignItem().getCampaign().getCode(),
                            campaignName,
                            line.pricing().toResponse(campaignName),
                            line.pricing().priceSource() != PriceSource.FLASH_SALE);
                }).toList());
    }

    private record QuoteLine(CartItem item, ProductVariant variant, VariantPricing pricing) {}

    private record QuoteLocalization(
            Map<Long, CatalogContentLocalizationService.LocalizedProduct> products,
            Map<Long, String> campaignNames) {}

    private record Quote(
            Instant serverTime,
            String pricingFingerprint,
            List<QuoteLine> lines,
            Map<Long, ProductVariant> variantMap,
            Coupon coupon,
            BigDecimal subtotal,
            BigDecimal eligibleSubtotal,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal finalAmount) {}

    private record ImageIndex(
            Map<Long, List<ProductImage>> byVariant,
            Map<Long, List<ProductImage>> byProduct) {}
}
