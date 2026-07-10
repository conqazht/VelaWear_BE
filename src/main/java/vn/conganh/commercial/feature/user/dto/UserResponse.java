package vn.conganh.commercial.feature.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.conganh.commercial.feature.role.Role;
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
        Instant updatedAt,
        boolean hasPassword,
        List<RoleSummaryResponse> roles
) {

    public static UserResponse fromEntity(User user) {
        return fromEntity(user, List.of());
    }

    public static UserResponse fromEntity(
            User user,
            List<RoleSummaryResponse> roles) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getBirthDate(),
                user.getAvatar(),
                user.getGender(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getPassword() != null && !user.getPassword().isBlank(),
                roles);
    }

    public record RoleSummaryResponse(
            Long id,
            String name
    ) {

        public static RoleSummaryResponse fromEntity(Role role) {
            return new RoleSummaryResponse(role.getId(), role.getName());
        }
    }
}
