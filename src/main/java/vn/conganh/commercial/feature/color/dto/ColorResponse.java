package vn.conganh.commercial.feature.color.dto;

import vn.conganh.commercial.feature.color.Color;

public record ColorResponse(
        Long id,
        String name,
        String hexCode,
        Integer sortOrder
) {
    public static ColorResponse fromEntity(Color color) {
        return new ColorResponse(
                color.getId(),
                color.getName(),
                color.getHexCode(),
                color.getSortOrder());
    }
}
