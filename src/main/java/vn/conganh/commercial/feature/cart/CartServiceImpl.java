package vn.conganh.commercial.feature.cart;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.cart.dto.CartFilterRequest;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCarts(CartFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(cartRepository.findAll(Specification.where(CartSpecification.build(filter)), pageable)
                .map(CartResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartById(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "id", id));
        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));
        return CartResponse.fromEntity(cart);
    }

    @Override
    @Transactional
    public CartResponse createCart(CreateCartRequest request) {
        if (cartRepository.existsByUserId(request.userId())) {
            throw new InvalidRequestException("Cart already exists for this user");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        Cart cart = new Cart();
        cart.setUser(user);

        return CartResponse.fromEntity(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public void deleteCart(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "id", id));
        cartRepository.delete(cart);
    }
}
