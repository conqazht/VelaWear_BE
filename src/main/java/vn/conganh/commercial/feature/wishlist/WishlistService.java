package vn.conganh.commercial.feature.wishlist;

import java.util.List;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

public interface WishlistService {

    List<WishlistResponse> getAllWishlists();

    List<WishlistResponse> getWishlistsByUserId(Long userId);

    List<WishlistResponse> getWishlistsByProductId(Long productId);

    WishlistResponse getWishlistById(Long id);

    WishlistResponse createWishlist(CreateWishlistRequest request);

    void deleteWishlist(Long id);
}
