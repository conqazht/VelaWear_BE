package vn.conganh.commercial.feature.wishlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistProductSummaryResponse;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;
import vn.conganh.commercial.feature.wishlist.dto.WishlistSelfItemResponse;
import vn.conganh.commercial.util.FilterSpecifications;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantPricingService variantPricingService;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllWishlists(WishlistFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(wishlistRepository.findAll(Specification.where(WishlistSpecification.build(filter)), pageable)
                .map(WishlistResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getWishlistsByUserId(Long userId, WishlistFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("userId", userId, filter == null ? null : filter.userId());
        WishlistFilterRequest scopedFilter = filter == null
                ? new WishlistFilterRequest(userId, null, null, null)
                : filter.withUserId(userId);

        return ResultPaginationDTO.fromPage(wishlistRepository.findAll(Specification.where(WishlistSpecification.build(scopedFilter)), pageable)
                .map(WishlistResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getWishlistsByProductId(Long productId, WishlistFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("productId", productId, filter == null ? null : filter.productId());
        WishlistFilterRequest scopedFilter = filter == null
                ? new WishlistFilterRequest(null, productId, null, null)
                : filter.withProductId(productId);

        return ResultPaginationDTO.fromPage(wishlistRepository.findAll(Specification.where(WishlistSpecification.build(scopedFilter)), pageable)
                .map(WishlistResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyWishlists(String email, String localeCode, Pageable pageable) {
        User user = findUserByEmail(email);
        Page<Wishlist> page = wishlistRepository.findActiveWishlistsByUserId(user.getId(), pageable);

        if (page.isEmpty()) {
            return ResultPaginationDTO.fromPage(page.map(w -> null));
        }

        List<Wishlist> wishlists = page.getContent();
        List<Long> productIds = wishlists.stream().map(w -> w.getProduct().getId()).distinct().toList();
        List<Long> categoryIds = wishlists.stream().map(w -> w.getProduct().getCategoryId()).distinct().toList();

        String defaultLocale = CatalogLocaleResolver.DEFAULT_LOCALE;

        Map<Long, ProductTranslation> reqProdTrans = productTranslationRepository.findByProductIdInAndLocaleCode(productIds, localeCode).stream().collect(Collectors.toMap(ProductTranslation::getProductId, t -> t));
        Map<Long, ProductTranslation> defProdTrans = localeCode.equals(defaultLocale) ? reqProdTrans : productTranslationRepository.findByProductIdInAndLocaleCode(productIds, defaultLocale).stream().collect(Collectors.toMap(ProductTranslation::getProductId, t -> t));

        Map<Long, CategoryTranslation> reqCatTrans = categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, localeCode).stream().collect(Collectors.toMap(CategoryTranslation::getCategoryId, t -> t));
        Map<Long, CategoryTranslation> defCatTrans = localeCode.equals(defaultLocale) ? reqCatTrans : categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, defaultLocale).stream().collect(Collectors.toMap(CategoryTranslation::getCategoryId, t -> t));

        Map<Long, List<ProductImage>> imagesByProd = productImageRepository.findByProductIdIn(productIds).stream().collect(Collectors.groupingBy(i -> i.getProduct().getId()));

        List<ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        Map<Long, VariantPricing> pricingByVariant = variantPricingService.resolve(variants, Instant.now(), user.getId());
        Map<Long, List<ProductVariant>> variantsByProd = variants.stream().collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        Map<Long, Category> categories = categoryRepository.findAllById(categoryIds).stream().collect(Collectors.toMap(Category::getId, c -> c));

        List<WishlistSelfItemResponse> content = wishlists.stream().map(w -> {
            Product p = w.getProduct();
            Long pId = p.getId();
            var pReq = reqProdTrans.get(pId);
            var pDef = defProdTrans.get(pId);

            var cReq = reqCatTrans.get(p.getCategoryId());
            var cDef = defCatTrans.get(p.getCategoryId());
            var category = categories.get(p.getCategoryId());

            String name = getValue(pReq != null ? pReq.getName() : null, pDef != null ? pDef.getName() : null, p.getName());
            String slug = getValue(pReq != null ? pReq.getSlug() : null, pDef != null ? pDef.getSlug() : null, p.getSlug());
            String desc = getValue(pReq != null ? pReq.getDescription() : null, pDef != null ? pDef.getDescription() : null, p.getDescription());
            String shortDesc = getValue(pReq != null ? pReq.getShortDescription() : null, pDef != null ? pDef.getShortDescription() : null, p.getDescription());

            String catName = category == null ? null : getValue(cReq != null ? cReq.getName() : null, cDef != null ? cDef.getName() : null, category.getName());
            String catSlug = category == null ? null : getValue(cReq != null ? cReq.getSlug() : null, cDef != null ? cDef.getSlug() : null, category.getSlug());

            String image = null;
            String thumbnail = null;
            List<ProductImage> imgs = imagesByProd.getOrDefault(pId, Collections.emptyList());
            if (!imgs.isEmpty()) {
                List<ProductImage> pLevelImgs = imgs.stream().filter(i -> i.getVariant() == null).sorted(Comparator.comparing(ProductImage::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
                if (pLevelImgs.isEmpty()) pLevelImgs = imgs;
                thumbnail = pLevelImgs.stream().filter(i -> Boolean.TRUE.equals(i.getIsThumbnail())).map(ProductImage::getImage).findFirst().orElse(null);
                if (thumbnail == null && !pLevelImgs.isEmpty()) thumbnail = pLevelImgs.get(0).getImage();
                image = thumbnail;
            }

            BigDecimal price = null;
            VariantPricingResponse pricingResp = null;
            List<ProductVariant> pVars = variantsByProd.getOrDefault(pId, Collections.emptyList());
            if (!pVars.isEmpty()) {
                var repVar = pVars.stream().min(Comparator.comparing((ProductVariant v) -> pricingByVariant.get(v.getId()).effectivePrice()).thenComparing(ProductVariant::getId)).orElse(pVars.get(0));
                var repPricing = pricingByVariant.get(repVar.getId());
                price = repVar.getPrice();
                pricingResp = repPricing.toResponse();
            }

            var summary = new WishlistProductSummaryResponse(
                pId, slug, name, desc, p.getCategoryId(), p.getSlug(), shortDesc, p.getStatus(), image, thumbnail, catName, catSlug, price, pricingResp
            );
            return new WishlistSelfItemResponse(
                w.getId(), w.getUser().getId(), pId, w.getCreatedAt(), summary
            );
        }).toList();

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta(
            page.getNumber() + 1, page.getSize(), page.getTotalPages(), page.getTotalElements()
        );
        return new ResultPaginationDTO(meta, content);
    }

    private String getValue(String req, String def, String base) {
        if (req != null && !req.isBlank()) return req;
        if (def != null && !def.isBlank()) return def;
        return base;
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistResponse getWishlistById(Long id) {
        return WishlistResponse.fromEntity(findWishlist(id));
    }

    @Override
    @Transactional
    public WishlistResponse createWishlist(CreateWishlistRequest request) {
        User user = findUser(request.userId());
        Product product = findProduct(request.productId());

        Optional<Wishlist> existingWishlist = wishlistRepository.findByUserIdAndProductId(request.userId(), request.productId());
        if (existingWishlist.isPresent()) {
            return WishlistResponse.fromEntity(existingWishlist.get());
        }

        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        wishlist.setProduct(product);

        return WishlistResponse.fromEntity(wishlistRepository.save(wishlist));
    }

    @Override
    @Transactional
    public WishlistResponse createMyWishlist(String email, Long productId) {
        User user = findUserByEmail(email);
        Product product = findProduct(productId);

        Optional<Wishlist> existingWishlist = wishlistRepository.findByUserIdAndProductId(user.getId(), productId);
        if (existingWishlist.isPresent()) {
            return WishlistResponse.fromEntity(existingWishlist.get());
        }

        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        wishlist.setProduct(product);

        return WishlistResponse.fromEntity(wishlistRepository.save(wishlist));
    }

    @Override
    @Transactional
    public void deleteWishlist(Long id) {
        wishlistRepository.delete(findWishlist(id));
    }

    @Override
    @Transactional
    public void deleteMyWishlist(String email, Long productId) {
        User user = findUserByEmail(email);
        wishlistRepository.findByUserIdAndProductId(user.getId(), productId)
                .ifPresent(wishlistRepository::delete);
    }

    private Wishlist findWishlist(Long id) {
        return wishlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", "id", id));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
