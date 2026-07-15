package vn.conganh.commercial.feature.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductTranslationRequest(
        @NotBlank @Size(max = 10) String localeCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 280) String slug,
        @Size(max = 500) String shortDescription,
        String description,
        String material,
        String careInstruction,
        @Size(max = 255) String seoTitle,
        @Size(max = 500) String seoDescription
) {}
