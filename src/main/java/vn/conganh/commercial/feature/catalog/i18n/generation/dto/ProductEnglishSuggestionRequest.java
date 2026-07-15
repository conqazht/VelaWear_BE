package vn.conganh.commercial.feature.catalog.i18n.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductEnglishSuggestionRequest(
        @Size(max = 64) String model,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String shortDescription,
        @Size(max = 20_000) String description,
        @Size(max = 5_000) String material,
        @Size(max = 5_000) String careInstruction,
        @Size(max = 255) String seoTitle,
        @Size(max = 500) String seoDescription
) {}
