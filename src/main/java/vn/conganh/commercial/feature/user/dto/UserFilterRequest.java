package vn.conganh.commercial.feature.user.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import vn.conganh.commercial.util.constant.UserGender;

public record UserFilterRequest(
        String fullName,
        String email,
        UserGender gender,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDateTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedTo
) {}
