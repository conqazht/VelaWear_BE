package vn.conganh.commercial.feature.wishlist;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;
import vn.conganh.commercial.util.FilterSpecifications;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final vn.conganh.commercial.feature.category.CategoryRepository categoryRepository;
    private final vn.conganh.commercial.feature.product.ProductTranslationRepository productTranslationRepository;
    private final vn.conganh.commercial.feature.category.CategoryTranslationRepository categoryTranslationRepository;
    private final vn.conganh.commercial.feature.product.ProductImageRepository productImageRepository;
    private final vn.conganh.commercial.feature.productvariant.ProductVariantRepository productVariantRepository;
    private final vn.conganh.commercial.feature.salecampaign.VariantPricingService variantPricingService;

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
        org.springframework.data.domain.Page<Wishlist> page = wishlistRepository.findActiveWishlistsByUserId(user.getId(), pageable);

        if (page.isEmpty()) {
            return ResultPaginationDTO.fromPage(page.map(w -> null));
        }

        java.util.List<Wishlist> wishlists = page.getContent();
        java.util.List<Long> productIds = wishlists.stream().map(w -> w.getProduct().getId()).distinct().toList();
        java.util.List<Long> categoryIds = wishlists.stream().map(w -> w.getProduct().getCategoryId()).distinct().toList();

        String defaultLocale = vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver.DEFAULT_LOCALE;

        java.util.Map<Long, vn.conganh.commercial.feature.product.ProductTranslation> reqProdTrans = productTranslationRepository.findByProductIdInAndLocaleCode(productIds, localeCode).stream().collect(java.util.stream.Collectors.toMap(t -> t.getProductId(), t -> t));
        java.util.Map<Long, vn.conganh.commercial.feature.product.ProductTranslation> defProdTrans = localeCode.equals(defaultLocale) ? reqProdTrans : productTranslationRepository.findByProductIdInAndLocaleCode(productIds, defaultLocale).stream().collect(java.util.stream.Collectors.toMap(t -> t.getProductId(), t -> t));

        java.util.Map<Long, vn.conganh.commercial.feature.category.CategoryTranslation> reqCatTrans = categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, localeCode).stream().collect(java.util.stream.Collectors.toMap(t -> t.getCategoryId(), t -> t));
        java.util.Map<Long, vn.conganh.commercial.feature.category.CategoryTranslation> defCatTrans = localeCode.equals(defaultLocale) ? reqCatTrans : categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, defaultLocale).stream().collect(java.util.stream.Collectors.toMap(t -> t.getCategoryId(), t -> t));

        java.util.Map<Long, java.util.List<vn.conganh.commercial.feature.product.ProductImage>> imagesByProd = productImageRepository.findByProductIdIn(productIds).stream().collect(java.util.stream.Collectors.groupingBy(i -> i.getProduct().getId()));

        java.util.List<vn.conganh.commercial.feature.productvariant.ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        java.util.Map<Long, vn.conganh.commercial.feature.salecampaign.VariantPricing> pricingByVariant = variantPricingService.resolve(variants, java.time.Instant.now(), user.getId());
        java.util.Map<Long, java.util.List<vn.conganh.commercial.feature.productvariant.ProductVariant>> variantsByProd = variants.stream().collect(java.util.stream.Collectors.groupingBy(v -> v.getProduct().getId()));

        java.util.Map<Long, vn.conganh.commercial.feature.category.Category> categories = categoryRepository.findAllById(categoryIds).stream().collect(java.util.stream.Collectors.toMap(c -> c.getId(), c -> c));

        java.util.List<vn.conganh.commercial.feature.wishlist.dto.WishlistSelfItemResponse> content = wishlists.stream().map(w -> {
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
            java.util.List<vn.conganh.commercial.feature.product.ProductImage> imgs = imagesByProd.getOrDefault(pId, java.util.Collections.emptyList());
            if (!imgs.isEmpty()) {
                java.util.List<vn.conganh.commercial.feature.product.ProductImage> pLevelImgs = imgs.stream().filter(i -> i.getVariant() == null).sorted(java.util.Comparator.comparing(vn.conganh.commercial.feature.product.ProductImage::getSortOrder, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
                if (pLevelImgs.isEmpty()) pLevelImgs = imgs;
                thumbnail = pLevelImgs.stream().filter(i -> Boolean.TRUE.equals(i.getIsThumbnail())).map(vn.conganh.commercial.feature.product.ProductImage::getImage).findFirst().orElse(null);
                if (thumbnail == null && !pLevelImgs.isEmpty()) thumbnail = pLevelImgs.get(0).getImage();
                image = thumbnail;
            }

            java.math.BigDecimal price = null;
            vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse pricingResp = null;
            java.util.List<vn.conganh.commercial.feature.productvariant.ProductVariant> pVars = variantsByProd.getOrDefault(pId, java.util.Collections.emptyList());
            if (!pVars.isEmpty()) {
                var repVar = pVars.stream().min(java.util.Comparator.comparing((vn.conganh.commercial.feature.productvariant.ProductVariant v) -> pricingByVariant.get(v.getId()).effectivePrice()).thenComparing(vn.conganh.commercial.feature.productvariant.ProductVariant::getId)).orElse(pVars.get(0));
                var repPricing = pricingByVariant.get(repVar.getId());
                price = repVar.getPrice();
                pricingResp = repPricing.toResponse();
            }

            var summary = new vn.conganh.commercial.feature.wishlist.dto.WishlistProductSummaryResponse(
                pId, slug, name, desc, p.getCategoryId(), p.getSlug(), shortDesc, p.getStatus(), image, thumbnail, catName, catSlug, price, pricingResp
            );
            return new vn.conganh.commercial.feature.wishlist.dto.WishlistSelfItemResponse(
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
