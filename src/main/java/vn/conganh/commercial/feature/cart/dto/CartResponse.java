package vn.conganh.commercial.feature.cart.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.cart.Cart;
import vn.conganh.commercial.feature.user.User;

public record CartResponse(
        Long id,
        Long userId,
        String userFullName,
        String userEmail,
        Instant createdAt
) {

    public static CartResponse fromEntity(Cart cart) {
        User user = cart.getUser();
        return new CartResponse(
                cart.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                cart.getCreatedAt());
    }
}
