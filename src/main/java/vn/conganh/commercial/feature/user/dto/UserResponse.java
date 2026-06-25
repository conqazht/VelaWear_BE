package vn.conganh.commercial.feature.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.UserGender;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        LocalDate birthDate,
        String avatar,
        UserGender gender,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getBirthDate(),
                user.getAvatar(),
                user.getGender(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
