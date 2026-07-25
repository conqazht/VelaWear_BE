package vn.conganh.commercial.feature.wishlist;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
@Tag(name = "Wishlists", description = "Customer wishlist management endpoints")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getWishlists(
            @ParameterObject WishlistFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getAllWishlists(filter, pageable)));
    }

    @GetMapping(path = "/me")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getMyWishlists(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String locale,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @ParameterObject Pageable pageable) {
        String resolvedLocale = localeResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(wishlistService.getMyWishlists(jwt.getSubject(), resolvedLocale, pageable)));
    }

    @PostMapping(path = "/me/{productId}")
    public ResponseEntity<ApiResponse<WishlistResponse>> createMyWishlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(wishlistService.createMyWishlist(jwt.getSubject(), productId)));
    }

    @DeleteMapping(path = "/me/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyWishlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        wishlistService.deleteMyWishlist(jwt.getSubject(), productId);
        return ResponseEntity.ok(ApiResponse.success(null));
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
