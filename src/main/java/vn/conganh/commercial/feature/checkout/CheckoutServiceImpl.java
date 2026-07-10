package vn.conganh.commercial.feature.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.EmptyCartException;
import vn.conganh.commercial.exception.InsufficientStockException;
import vn.conganh.commercial.exception.CouponNotValidException;
import vn.conganh.commercial.exception.InvalidOrderTransitionException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.cart.CartItemRepository;
import vn.conganh.commercial.feature.cart.CartRepository;
import vn.conganh.commercial.feature.checkout.dto.CheckoutItemResponse;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.coupon.CouponRepository;
import vn.conganh.commercial.feature.coupon.CouponUsage;
import vn.conganh.commercial.feature.coupon.CouponUsageRepository;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.order.OrderStatusHistory;
import vn.conganh.commercial.feature.order.OrderStatusHistoryRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.productvariant.InventoryLog;
import vn.conganh.commercial.feature.productvariant.InventoryLogRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CouponType;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@Slf4j
@RequiredArgsConstructor
@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request, String userEmail) {
        log.info("[VelaWear/Checkout] - ACTION: Start checkout for user {}", userEmail);
        
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(EmptyCartException::new);
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new EmptyCartException();
        }

        List<Long> variantIds = items.stream()
                .map(CartItem::getVariantId)
                .distinct()
                .toList();
        List<ProductVariant> variants = productVariantRepository.findAllByIdInAndDeletedAtIsNull(variantIds);

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        for (CartItem item : items) {
            ProductVariant variant = variantMap.get(item.getVariantId());
            if (variant == null || !"ACTIVE".equals(variant.getStatus())) {
                throw new InvalidRequestException("Product variant not available: " + item.getVariantId());
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            ProductVariant variant = variantMap.get(item.getVariantId());
            BigDecimal effectivePrice = variant.getSalePrice() != null ? variant.getSalePrice() : variant.getPrice();
            subtotal = subtotal.add(effectivePrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal shippingFee = request.shippingFee() != null ? request.shippingFee() : BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;

        String orderCode = "VELA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(orderCode);
        order.setStatus("PENDING");
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(discountAmount);
        order.setFinalAmount(subtotal.add(shippingFee));
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus("UNPAID");
        order = orderRepository.save(order);

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            Coupon coupon = couponRepository.findByCode(request.couponCode())
                    .orElseThrow(() -> new CouponNotValidException("Coupon not found"));

            if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
                throw new CouponNotValidException("Order subtotal does not meet minimum amount");
            }

            if (coupon.getType() == CouponType.PERCENTAGE) {
                discountAmount = subtotal.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscount() != null && discountAmount.compareTo(coupon.getMaxDiscount()) > 0) {
                    discountAmount = coupon.getMaxDiscount();
                }
            } else {
                discountAmount = coupon.getValue();
            }

            int consumed = couponRepository.consumeUsage(coupon.getId(), Instant.now());
            if (consumed == 0) {
                throw new CouponNotValidException("Coupon is no longer available or usage limit reached");
            }

            CouponUsage couponUsage = new CouponUsage();
            couponUsage.setCoupon(coupon);
            couponUsage.setUser(user);
            couponUsage.setOrder(order);
            couponUsage.setDiscountAmount(discountAmount);
            couponUsageRepository.save(couponUsage);

            order.setDiscountAmount(discountAmount);
            BigDecimal finalAmount = subtotal.add(shippingFee).subtract(discountAmount);
            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }
            order.setFinalAmount(finalAmount);
            order = orderRepository.save(order);
        }

        for (CartItem item : items) {
            int reserved = productVariantRepository.decrementStock(item.getVariantId(), item.getQuantity());
            if (reserved == 0) {
                ProductVariant variant = variantMap.get(item.getVariantId());
                throw new InsufficientStockException(
                        variant.getSku() != null ? variant.getSku() : "ID:" + item.getVariantId());
            }
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem item : items) {
            ProductVariant variant = variantMap.get(item.getVariantId());
            Product product = variant.getProduct();
            BigDecimal effectivePrice = variant.getSalePrice() != null ? variant.getSalePrice() : variant.getPrice();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariantId(variant.getId());
            orderItem.setProductName(product.getName());
            orderItem.setVariantName(buildVariantName(variant));
            orderItem.setSku(variant.getSku());
            orderItem.setImage(resolveItemImage(variant, product));
            orderItem.setPrice(effectivePrice);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setSubtotal(effectivePrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItem.setStatus("PENDING");
            orderItems.add(orderItem);
        }
        orderItems = orderItemRepository.saveAll(orderItems);

        for (OrderItem orderItem : orderItems) {
            ProductVariant variant = variantMap.get(orderItem.getVariantId());
            writeInventoryLog(variant, -orderItem.getQuantity(), "ORDER", "ORDER_ITEM", orderItem.getId());
        }

        Long paymentId = null;
        boolean isOnlinePayment = !"COD".equalsIgnoreCase(request.paymentMethod());
        if (isOnlinePayment) {
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setProvider(PaymentProvider.valueOf(request.paymentMethod().toUpperCase()));
            payment.setAmount(order.getFinalAmount());
            payment.setStatus(PaymentStatus.PENDING);
            payment = paymentRepository.save(payment);
            paymentId = payment.getId();
        }

        cartItemRepository.deleteByCartId(cart.getId());

        List<CheckoutItemResponse> itemResponses = orderItems.stream()
                .map(CheckoutItemResponse::fromEntity)
                .toList();
                
        log.info("[VelaWear/Checkout] - ACTION: Checkout successful for user {}, order code {}", userEmail, orderCode);
        return CheckoutResponse.fromEntity(order, itemResponses, paymentId);
    }

    @Override
    @Transactional
    public void cancelOrder(long orderId, String userEmail) {
        log.info("[VelaWear/Checkout] - ACTION: Start cancel order id {} for user {}", orderId, userEmail);
        
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new InvalidRequestException("Order does not belong to user");
        }

        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw new InvalidOrderTransitionException(order.getStatus(), "CANCELLED");
        }

        restoreOrderResources(order);

        String previousStatus = order.getStatus();
        order.setStatus("CANCELLED");
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(previousStatus);
        history.setToStatus("CANCELLED");
        orderStatusHistoryRepository.save(history);
        
        log.info("[VelaWear/Checkout] - ACTION: Cancel order successful for id {}", orderId);
    }

    @Override
    @Transactional
    public void handlePaymentFailure(long orderId) {
        log.info("[VelaWear/Checkout] - ACTION: Handle payment failure for order id {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (isTerminalStatus(order.getStatus())) {
            return;
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw new InvalidOrderTransitionException(order.getStatus(), "CANCELLED");
        }

        restoreOrderResources(order);

        String previousStatus = order.getStatus();
        order.setStatus("CANCELLED");
        order.setPaymentStatus("FAILED");
        orderRepository.save(order);

        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        });

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(previousStatus);
        history.setToStatus("CANCELLED");
        orderStatusHistoryRepository.save(history);
        
        log.info("[VelaWear/Checkout] - ACTION: Handled payment failure for order id {}", orderId);
    }

    private void writeInventoryLog(ProductVariant variant, int changeQuantity, String type, String referenceType, Long referenceId) {
        InventoryLog logRecord = new InventoryLog();
        logRecord.setVariant(variant);
        logRecord.setChangeQuantity(changeQuantity);
        logRecord.setType(type);
        logRecord.setReferenceType(referenceType);
        logRecord.setReferenceId(referenceId);
        inventoryLogRepository.save(logRecord);
    }

    private String buildVariantName(ProductVariant variant) {
        String colorName = variant.getColor() != null ? variant.getColor().getName() : "";
        String sizeName = variant.getSize() != null ? variant.getSize().getName() : "";
        
        if (!colorName.isEmpty() && !sizeName.isEmpty()) {
            return colorName + " / " + sizeName;
        } else if (!colorName.isEmpty()) {
            return colorName;
        } else if (!sizeName.isEmpty()) {
            return sizeName;
        }
        return "";
    }

    private String resolveItemImage(ProductVariant variant, Product product) {
        Optional<String> variantImage = selectImage(productImageRepository.findByVariantId(variant.getId()));
        if (variantImage.isPresent()) {
            return variantImage.get();
        }
        return selectImage(productImageRepository.findByProductId(product.getId())).orElse(null);
    }

    private Optional<String> selectImage(List<ProductImage> images) {
        if (images == null) {
            return Optional.empty();
        }
        return images.stream()
                .sorted(Comparator
                        .comparing((ProductImage image) -> !Boolean.TRUE.equals(image.getIsThumbnail()))
                        .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ProductImage::getImage)
                .findFirst();
    }

    private boolean isTerminalStatus(String status) {
        return "CANCELLED".equals(status) || "COMPLETED".equals(status) || "REFUNDED".equals(status);
    }

    private void restoreOrderResources(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : orderItems) {
            productVariantRepository.restoreStock(item.getVariantId(), item.getQuantity());
            
            productVariantRepository.findByIdAndDeletedAtIsNull(item.getVariantId()).ifPresent(variant -> {
                writeInventoryLog(variant, item.getQuantity(), "CANCEL", "ORDER_ITEM", item.getId());
            });
        }

        couponUsageRepository.findByOrderId(order.getId()).ifPresent(couponUsage -> {
            couponRepository.releaseUsage(couponUsage.getCoupon().getId());
            couponUsageRepository.delete(couponUsage);
        });
    }
}
