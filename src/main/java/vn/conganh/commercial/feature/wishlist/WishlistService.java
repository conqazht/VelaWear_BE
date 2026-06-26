package vn.conganh.commercial.feature.wishlist;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

public interface WishlistService {

    ResultPaginationDTO getAllWishlists(Pageable pageable);

    ResultPaginationDTO getWishlistsByUserId(Long userId, Pageable pageable);

    ResultPaginationDTO getWishlistsByProductId(Long productId, Pageable pageable);

    WishlistResponse getWishlistById(Long id);

    WishlistResponse createWishlist(CreateWishlistRequest request);

    void deleteWishlist(Long id);
}
