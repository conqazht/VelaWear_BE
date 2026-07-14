package vn.conganh.commercial.feature.cart;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.cart.dto.CartFilterRequest;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.cart.dto.ReplaceCartItemsRequest;

public interface CartService {

    ResultPaginationDTO getAllCarts(CartFilterRequest filter, Pageable pageable);

    CartResponse getCartById(Long id);

    CartResponse getCartByUserId(Long userId);

    CartResponse createCart(CreateCartRequest request);

    CartResponse getMyCart(String email);

    CartResponse replaceMyCartItems(String email, ReplaceCartItemsRequest request);

    void deleteCart(Long id);
}
