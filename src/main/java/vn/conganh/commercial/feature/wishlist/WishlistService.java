package vn.conganh.commercial.feature.wishlist;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

public interface WishlistService {

    ResultPaginationDTO getAllWishlists(WishlistFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getWishlistsByUserId(Long userId, WishlistFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getWishlistsByProductId(Long productId, WishlistFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getMyWishlists(String email, Pageable pageable);

    WishlistResponse getWishlistById(Long id);

    WishlistResponse createWishlist(CreateWishlistRequest request);

    WishlistResponse createMyWishlist(String email, Long productId);

    void deleteWishlist(Long id);

    void deleteMyWishlist(String email, Long productId);
}
