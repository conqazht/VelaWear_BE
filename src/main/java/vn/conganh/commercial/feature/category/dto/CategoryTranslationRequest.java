package vn.conganh.commercial.feature.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryTranslationRequest(
        @NotBlank @Size(max = 10) String localeCode,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 180) String slug,
        String description,
        @Size(max = 255) String seoTitle,
        @Size(max = 500) String seoDescription
) {}
