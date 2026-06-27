package vn.conganh.commercial.feature.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.conganh.commercial.feature.permission.Permission;
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
        List<RoleSummaryResponse> roles,
        List<PermissionSummaryResponse> permissions
) {

    public static UserResponse fromEntity(User user) {
        return fromEntity(user, List.of(), List.of());
    }

    public static UserResponse fromEntity(User user, List<RoleSummaryResponse> roles) {
        return fromEntity(user, roles, List.of());
    }

    public static UserResponse fromEntity(
            User user,
            List<RoleSummaryResponse> roles,
            List<PermissionSummaryResponse> permissions) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getBirthDate(),
                user.getAvatar(),
                user.getGender(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roles,
                permissions);
    }

    public record RoleSummaryResponse(
            Long id,
            String name
    ) {

        public static RoleSummaryResponse fromEntity(Role role) {
            return new RoleSummaryResponse(role.getId(), role.getName());
        }
    }

    public record PermissionSummaryResponse(
            Long id,
            String name,
            String apiPath,
            String method,
            String module
    ) {

        public static PermissionSummaryResponse fromEntity(Permission permission) {
            return new PermissionSummaryResponse(
                    permission.getId(),
                    permission.getName(),
                    permission.getApiPath(),
                    permission.getMethod(),
                    permission.getModule());
        }
    }
}
