package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
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
        query.assertPriceRangeValid();
        StorefrontCatalogSnapshot snapshot = catalogLoader.load(localeCode);
        StorefrontCatalogEvaluation evaluation = filterEngine.evaluate(snapshot, query);
        return buildResponse(query, evaluation, snapshot);
    }

    private StorefrontCatalogResponse buildResponse(
            StorefrontProductQuery query,
            StorefrontCatalogEvaluation evaluation,
            StorefrontCatalogSnapshot snapshot) {
        int page = query.resolvedPage();
        int pageSize = query.resolvedSize();
        List<Candidate> pageCandidates = paginate(evaluation.candidates(), page, pageSize);
        List<ProductResponse> products = pageCandidates.stream()
                .map(candidate -> productMapper.toResponse(candidate, snapshot))
                .toList();
        int total = evaluation.candidates().size();
        int pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta(page, pageSize, pages, total);
        return new StorefrontCatalogResponse(products, meta, evaluation.facets());
    }

    private List<Candidate> paginate(List<Candidate> candidates, int page, int pageSize) {
        int total = candidates.size();
        int fromIndex = (int) Math.min((long) (page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return candidates.subList(fromIndex, toIndex);
    }
}
