package vn.conganh.commercial.feature.storefrontcatalog;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogEvaluation.Candidate;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@Service
@RequiredArgsConstructor
class StorefrontCatalogServiceImpl implements StorefrontCatalogService {

    private final StorefrontCatalogLoader catalogLoader;
    private final StorefrontCatalogFilterEngine filterEngine;
    private final StorefrontCatalogProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public StorefrontCatalogResponse getProducts(StorefrontProductQuery query, String localeCode) {
        validatePriceRange(query.minPrice(), query.maxPrice());
        StorefrontCatalogSnapshot snapshot = catalogLoader.load(localeCode);
        StorefrontCatalogEvaluation evaluation = filterEngine.evaluate(snapshot, query);

        int page = query.resolvedPage();
        int pageSize = query.resolvedSize();
        int total = evaluation.candidates().size();
        long requestedStart = (long) (page - 1) * pageSize;
        int fromIndex = (int) Math.min(requestedStart, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Candidate> pageCandidates = evaluation.candidates().subList(fromIndex, toIndex);
        List<ProductResponse> products = pageCandidates.stream()
                .map(candidate -> productMapper.toResponse(candidate, snapshot))
                .toList();
        int pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta(page, pageSize, pages, total);
        return new StorefrontCatalogResponse(products, meta, evaluation.facets());
    }

    private void validatePriceRange(BigDecimal minimumPrice, BigDecimal maximumPrice) {
        if (minimumPrice != null && maximumPrice != null && minimumPrice.compareTo(maximumPrice) > 0) {
            throw new InvalidRequestException("Minimum price must not be greater than maximum price");
        }
    }
}
