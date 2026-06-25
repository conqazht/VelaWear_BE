package vn.conganh.commercial.feature.cart;

import java.util.List;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;

public interface CartService {

    List<CartResponse> getAllCarts();

    CartResponse getCartById(Long id);

    CartResponse getCartByUserId(Long userId);

    CartResponse createCart(CreateCartRequest request);

    void deleteCart(Long id);
}
