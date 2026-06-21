package vn.conganh.commercial.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.conganh.commercial.feature.user.UserGender;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @Size(max = 500, message = "Avatar must be at most 500 characters")
        String avatar,

        @NotNull(message = "Gender is required")
        UserGender gender
) {
}
