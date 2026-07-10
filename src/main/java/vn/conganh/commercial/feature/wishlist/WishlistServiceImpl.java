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
    public ResultPaginationDTO getMyWishlists(String email, Pageable pageable) {
        User user = findUserByEmail(email);
        return ResultPaginationDTO.fromPage(wishlistRepository.findByUserId(user.getId(), pageable)
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
