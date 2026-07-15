package vn.conganh.commercial.feature.cart;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.cart.dto.CartFilterRequest;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.cart.dto.ReplaceCartItemsRequest;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Shopping cart management endpoints")
public class CartController {

    private final CartService cartService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getCarts(
            @ParameterObject CartFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getAllCarts(filter, pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @PathVariable Long id,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCartById(id, localeResolver.resolve(locale, acceptLanguage))));
    }

    @GetMapping(path = "/user/{userId}")
    public ResponseEntity<ApiResponse<CartResponse>> getCartByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getCartByUserId(userId, localeResolver.resolve(locale, acceptLanguage))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CartResponse>> createCart(@RequestBody @Valid CreateCartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(cartService.createCart(request)));
    }

    @GetMapping(path = "/me")
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.getMyCart(jwt.getSubject(), localeResolver.resolve(locale, acceptLanguage))));
    }

    @PutMapping(path = "/me/items")
    public ResponseEntity<ApiResponse<CartResponse>> replaceMyCartItems(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ReplaceCartItemsRequest request,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(cartService.replaceMyCartItems(
                jwt.getSubject(),
                request,
                localeResolver.resolve(locale, acceptLanguage))));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCart(@PathVariable Long id) {
        cartService.deleteCart(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
