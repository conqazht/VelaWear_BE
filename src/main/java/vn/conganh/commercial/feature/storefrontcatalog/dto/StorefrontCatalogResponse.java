package vn.conganh.commercial.feature.storefrontcatalog.dto;

import java.math.BigDecimal;
import java.util.List;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.product.dto.ProductResponse;

public record StorefrontCatalogResponse(
        List<ProductResponse> result,
        ResultPaginationDTO.Meta meta,
        Facets facets
) {

    public record Facets(
            List<CategoryFacet> categories,
            List<ColorFacet> colors,
            List<SizeFacet> sizes,
            PriceRange priceRange
    ) {}

    public record CategoryFacet(
            Long id,
            String name,
            String slug,
            long count
    ) {}

    public record ColorFacet(
            Long id,
            String name,
            String hexCode,
            Integer sortOrder,
            long count
    ) {}

    public record SizeFacet(
            Long id,
            String name,
            Integer sortOrder,
            long count
    ) {}

    public record PriceRange(
            BigDecimal min,
            BigDecimal max
    ) {}
}
