package vn.conganh.commercial.feature.cart;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;

public interface CartService {

    ResultPaginationDTO getAllCarts(Pageable pageable);

    CartResponse getCartById(Long id);

    CartResponse getCartByUserId(Long userId);

    CartResponse createCart(CreateCartRequest request);

    void deleteCart(Long id);
}
