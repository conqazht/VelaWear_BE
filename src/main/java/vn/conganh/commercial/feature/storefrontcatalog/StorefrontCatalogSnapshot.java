package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.List;
import java.util.Map;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

record StorefrontCatalogSnapshot(
        List<Item> items,
        Map<Long, List<ProductImage>> imagesByProduct,
        Map<Long, String> campaignNames
) {

    record Item(
            Product product,
            ProductTranslation translation,
            List<String> translationLocales,
            Category category,
            String categoryName,
            List<Offer> offers,
            ReviewScore reviewScore
    ) {}

    record Offer(
            ProductVariant variant,
            VariantPricing pricing
    ) {}

    record ReviewScore(
            double averageRating,
            long reviewCount
    ) {
        static final ReviewScore EMPTY = new ReviewScore(0.0d, 0L);
    }
}
