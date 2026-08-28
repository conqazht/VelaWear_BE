package vn.conganh.commercial.feature.wishlist;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.CatalogDisplayService;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
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
    private final CatalogDisplayService catalogDisplayService;

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
        return ResultPaginationDTO.fromPage(wishlistRepository.findAll(Specification.where(WishlistSpecification.build(filter)), pageable)
                .map(WishlistResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getWishlistsByProductId(Long productId, WishlistFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("productId", productId, filter == null ? null : filter.productId());
        return ResultPaginationDTO.fromPage(wishlistRepository.findAll(Specification.where(WishlistSpecification.build(filter)), pageable)
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
        List<Product> products = wishlists.stream().map(Wishlist::getProduct).toList();
        Map<Long, WishlistProductSummaryResponse> summaryMap = catalogDisplayService
                .assembleWishlistSummaries(products, localeCode, user.getId());

        List<WishlistSelfItemResponse> content = wishlists.stream().map(w -> {
            Long pId = w.getProduct().getId();
            return new WishlistSelfItemResponse(
                    w.getId(),
                    w.getUser().getId(),
                    pId,
                    w.getCreatedAt(),
                    summaryMap.get(pId));
        }).toList();

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta(
                page.getNumber() + 1, page.getSize(), page.getTotalPages(), page.getTotalElements());
        return new ResultPaginationDTO(meta, content);
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
