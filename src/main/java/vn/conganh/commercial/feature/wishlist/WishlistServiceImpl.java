package vn.conganh.commercial.feature.wishlist;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WishlistResponse> getAllWishlists() {
        return wishlistRepository.findAll().stream()
                .map(WishlistResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistResponse> getWishlistsByUserId(Long userId) {
        return wishlistRepository.findByUserId(userId).stream()
                .map(WishlistResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistResponse> getWishlistsByProductId(Long productId) {
        return wishlistRepository.findByProductId(productId).stream()
                .map(WishlistResponse::fromEntity)
                .toList();
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
