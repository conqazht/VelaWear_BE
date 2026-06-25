package vn.conganh.commercial.feature.size.dto;

import vn.conganh.commercial.feature.size.Size;

public record SizeResponse(
        Long id,
        String name,
        Integer sortOrder
) {
    public static SizeResponse fromEntity(Size size) {
        return new SizeResponse(
                size.getId(),
                size.getName(),
                size.getSortOrder());
    }
}
