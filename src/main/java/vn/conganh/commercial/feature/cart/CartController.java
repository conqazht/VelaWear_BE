package vn.conganh.commercial.feature.cart;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getCarts(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getAllCarts(pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getCartById(id)));
    }

    @GetMapping(path = "/user/{userId}")
    public ResponseEntity<ApiResponse<CartResponse>> getCartByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getCartByUserId(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CartResponse>> createCart(@RequestBody @Valid CreateCartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(cartService.createCart(request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCart(@PathVariable Long id) {
        cartService.deleteCart(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
