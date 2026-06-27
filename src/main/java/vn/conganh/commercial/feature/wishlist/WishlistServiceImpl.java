package vn.conganh.commercial.feature.wishlist;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;
import vn.conganh.commercial.util.FilterSpecifications;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

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
    public WishlistResponse getWishlistById(Long id) {
        return WishlistResponse.fromEntity(findWishlist(id));
    }

    @Override
    @Transactional
    public WishlistResponse createWishlist(CreateWishlistRequest request) {
        User user = findUser(request.userId());
        Product product = findProduct(request.productId());

        if (wishlistRepository.existsByUserIdAndProductId(request.userId(), request.productId())) {
            throw new InvalidRequestException("Wishlist entry already exists for this user and product");
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

    private Wishlist findWishlist(Long id) {
        return wishlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", "id", id));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
