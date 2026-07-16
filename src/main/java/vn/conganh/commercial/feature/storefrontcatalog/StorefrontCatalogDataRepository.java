package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;

@Repository
@RequiredArgsConstructor
class StorefrontCatalogDataRepository {

    private static final String ACTIVE = "ACTIVE";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final StorefrontReviewStatsRepository reviewStatsRepository;

    List<Product> findVisibleProducts() {
        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> ACTIVE.equals(product.getStatus()))
                .sorted(Comparator.comparing(Product::getId))
                .toList();
    }

    Map<Long, Category> findVisibleCategories(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .filter(category -> category.getDeletedAt() == null)
                .filter(category -> ACTIVE.equals(category.getStatus()))
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    List<ProductVariant> findVisibleVariants(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds).stream()
                .filter(variant -> ACTIVE.equals(variant.getStatus()))
                .sorted(Comparator.comparing(ProductVariant::getId))
                .toList();
    }

    List<ProductImage> findImages(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productImageRepository.findByProductIdIn(productIds);
    }

    Map<Long, StorefrontCatalogSnapshot.ReviewScore> findReviewScores(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return reviewStatsRepository.findScoresByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> new StorefrontCatalogSnapshot.ReviewScore(
                                row[1] == null ? 0.0d : ((Number) row[1]).doubleValue(),
                                ((Number) row[2]).longValue())));
    }
}
