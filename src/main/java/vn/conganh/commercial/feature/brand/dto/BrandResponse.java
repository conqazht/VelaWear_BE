package vn.conganh.commercial.feature.brand.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.brand.Brand;

public record BrandResponse(
        Long id,
        String name,
        String slug,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BrandResponse fromEntity(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                brand.getStatus(),
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }
}
