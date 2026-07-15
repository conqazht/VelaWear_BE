package vn.conganh.commercial.feature.cart;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.cart.dto.CartFilterRequest;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CartItemResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.cart.dto.ReplaceCartItemsRequest;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final VariantPricingService variantPricingService;
    private final CatalogContentLocalizationService localizationService;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCarts(CartFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(cartRepository.findAll(Specification.where(CartSpecification.build(filter)), pageable)
                .map(CartResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartById(Long id) {
        return getCartById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartById(Long id, String localeCode) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "id", id));
        return toDetailedResponse(cart, localeCode);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        return getCartByUserId(userId, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId, String localeCode) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));
        return toDetailedResponse(cart, localeCode);
    }

    @Override
    @Transactional
    public CartResponse createCart(CreateCartRequest request) {
        if (cartRepository.existsByUserId(request.userId())) {
            throw new InvalidRequestException("Cart already exists for this user");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        Cart cart = new Cart();
        cart.setUser(user);

        return CartResponse.fromEntity(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartResponse getMyCart(String email) {
        return getMyCart(email, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public CartResponse getMyCart(String email, String localeCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return toDetailedResponse(getOrCreateCart(user), localeCode);
    }

    @Override
    @Transactional
    public CartResponse replaceMyCartItems(String email, ReplaceCartItemsRequest request) {
        return replaceMyCartItems(email, request, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public CartResponse replaceMyCartItems(
            String email,
            ReplaceCartItemsRequest request,
            String localeCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        getOrCreateCart(user);
        // Checkout uses the same cart-row write lock. This makes a cart replacement
        // linearizable with checkout reading and clearing the cart.
        Cart cart = cartRepository.findWithLockByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", user.getId()));

        Map<Long, Integer> requestedItems = request.items().stream()
                .collect(Collectors.toMap(
                        ReplaceCartItemsRequest.Item::variantId,
                        ReplaceCartItemsRequest.Item::quantity,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));

        List<ProductVariant> variants = productVariantRepository
                .findAllByIdInAndDeletedAtIsNull(List.copyOf(requestedItems.keySet()));
        if (variants.size() != requestedItems.size()) {
            throw new InvalidRequestException("One or more product variants do not exist");
        }

        Map<Long, CartItem> existingItems = cartItemRepository.findByCartId(cart.getId()).stream()
                .collect(Collectors.toMap(CartItem::getVariantId, Function.identity()));
        Set<Long> requestedVariantIds = requestedItems.keySet();
        cartItemRepository.deleteAll(existingItems.values().stream()
                .filter(item -> !requestedVariantIds.contains(item.getVariantId()))
                .toList());

        List<CartItem> itemsToSave = requestedItems.entrySet().stream()
                .map(entry -> {
                    CartItem item = existingItems.getOrDefault(entry.getKey(), new CartItem());
                    item.setCart(cart);
                    item.setVariantId(entry.getKey());
                    item.setQuantity(entry.getValue());
                    return item;
                })
                .toList();
        cartItemRepository.saveAll(itemsToSave);
        cartItemRepository.flush();
        return toDetailedResponse(cart, localeCode);
    }

    @Override
    @Transactional
    public void deleteCart(Long id) {
        Cart cart = cartRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "id", id));
        cartRepository.delete(cart);
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    private CartResponse toDetailedResponse(Cart cart, String localeCode) {
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        Map<Long, ProductVariant> variants = productVariantRepository
                .findAllByIdInAndDeletedAtIsNull(cartItems.stream().map(CartItem::getVariantId).toList())
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, vn.conganh.commercial.feature.salecampaign.VariantPricing> pricingByVariant =
                variantPricingService.resolve(variants.values(), java.time.Instant.now(), cart.getUser().getId());
        Map<Long, CatalogContentLocalizationService.LocalizedProduct> localizedProducts =
                localizationService.localizeProducts(
                        variants.values().stream().map(ProductVariant::getProduct).toList(),
                        localeCode);
        Map<Long, String> localizedCampaignNames = localizationService.localizeCampaignNames(
                pricingByVariant.values().stream()
                        .filter(pricing -> pricing != null && pricing.campaignItem() != null)
                        .map(pricing -> pricing.campaignItem().getCampaign())
                        .toList(),
                localeCode);
        List<Long> productIds = variants.values().stream()
                .map(ProductVariant::getProduct)
                .filter(java.util.Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .toList();
        Map<Long, String> imagesByProductId = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .sorted(Comparator
                                .comparing(ProductImage::getIsThumbnail,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(ProductImage::getSortOrder,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.toMap(
                                image -> image.getProduct().getId(),
                                ProductImage::getImage,
                                (preferred, ignored) -> preferred));

        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(item -> {
                    ProductVariant variant = variants.get(item.getVariantId());
                    if (variant == null) {
                        return new CartItemResponse(item.getId(), item.getVariantId(), null, null,
                                "Unavailable product", null, null, null, null,
                                BigDecimal.ZERO, BigDecimal.ZERO, null, item.getQuantity());
                    }
                    Product product = variant.getProduct();
                    CatalogContentLocalizationService.LocalizedProduct localizedProduct = product == null
                            ? null
                            : localizedProducts.get(product.getId());
                    var pricing = pricingByVariant.get(variant.getId());
                    BigDecimal price = pricing == null ? variant.getPrice() : pricing.effectivePrice();
                    return new CartItemResponse(
                            item.getId(),
                            variant.getId(),
                            product != null ? product.getId() : null,
                            localizedProduct != null ? localizedProduct.slug() : null,
                            localizedProduct != null ? localizedProduct.name() : "Unavailable product",
                            product != null ? imagesByProductId.get(product.getId()) : null,
                            variant.getSku(),
                            variant.getColor() != null ? variant.getColor().getName() : null,
                            variant.getSize() != null ? variant.getSize().getName() : null,
                            variant.getPrice(),
                            price,
                            localizedPricing(pricing, localizedCampaignNames),
                            item.getQuantity());
                })
                .toList();
        return CartResponse.fromEntity(cart, itemResponses);
    }

    private vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse localizedPricing(
            vn.conganh.commercial.feature.salecampaign.VariantPricing pricing,
            Map<Long, String> campaignNames) {
        if (pricing == null) {
            return null;
        }
        if (pricing.campaignItem() == null) {
            return pricing.toResponse();
        }
        var campaign = pricing.campaignItem().getCampaign();
        return pricing.toResponse(campaignNames.getOrDefault(campaign.getId(), campaign.getName()));
    }
}
