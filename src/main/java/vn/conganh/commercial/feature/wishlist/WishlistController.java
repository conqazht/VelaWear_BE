package vn.conganh.commercial.feature.wishlist;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
@Tag(name = "Wishlists", description = "Customer wishlist management endpoints")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getWishlists(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long productId,
            @ParameterObject Pageable pageable) {
        if (userId != null) {
            return ResponseEntity.ok(ApiResponse.success(wishlistService.getWishlistsByUserId(userId, pageable)));
        }
        if (productId != null) {
            return ResponseEntity.ok(ApiResponse.success(wishlistService.getWishlistsByProductId(productId, pageable)));
        }
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getAllWishlists(pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getWishlistById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> createWishlist(
            @RequestBody @Valid CreateWishlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(wishlistService.createWishlist(request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWishlist(@PathVariable Long id) {
        wishlistService.deleteWishlist(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
