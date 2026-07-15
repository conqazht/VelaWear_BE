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

    CartResponse getCartById(Long id, String localeCode);

    CartResponse getCartByUserId(Long userId);

    CartResponse getCartByUserId(Long userId, String localeCode);

    CartResponse createCart(CreateCartRequest request);

    CartResponse getMyCart(String email);

    CartResponse getMyCart(String email, String localeCode);

    CartResponse replaceMyCartItems(String email, ReplaceCartItemsRequest request);

    CartResponse replaceMyCartItems(String email, ReplaceCartItemsRequest request, String localeCode);

    void deleteCart(Long id);
}
