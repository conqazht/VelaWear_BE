package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogLocalization.ProductLocalization;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Item;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Offer;

@Component
@RequiredArgsConstructor
class StorefrontCatalogLoader {

    private final StorefrontCatalogDataRepository dataRepository;
    private final StorefrontCatalogLocalization localization;
    private final VariantPricingService variantPricingService;

    StorefrontCatalogSnapshot load(String localeCode) {
        List<Product> visibleProducts = dataRepository.findVisibleProducts();
        Map<Long, Category> categories = dataRepository.findVisibleCategories(visibleProducts.stream()
                .map(Product::getCategoryId)
                .distinct()
                .toList());
        List<Product> categorizedProducts = visibleProducts.stream()
                .filter(product -> categories.containsKey(product.getCategoryId()))
                .toList();
        List<Long> productIds = categorizedProducts.stream().map(Product::getId).toList();
        List<ProductVariant> variants = dataRepository.findVisibleVariants(productIds);
        Map<Long, VariantPricing> pricingByVariant = variantPricingService.resolve(variants);
        Map<Long, List<Offer>> offersByProduct = variants.stream()
                .filter(variant -> pricingByVariant.containsKey(variant.getId()))
                .map(variant -> new Offer(variant, pricingByVariant.get(variant.getId())))
                .collect(Collectors.groupingBy(offer -> offer.variant().getProduct().getId()));
        List<Product> sellableProducts = categorizedProducts.stream()
                .filter(product -> !offersByProduct.getOrDefault(product.getId(), List.of()).isEmpty())
                .toList();
        List<Long> sellableProductIds = sellableProducts.stream().map(Product::getId).toList();

        Map<Long, ProductLocalization> productLocalizations = localization.resolveProducts(
                sellableProducts,
                localeCode);
        Map<Long, String> categoryNames = localization.resolveCategoryNames(categories.values(), localeCode);
        Map<Long, StorefrontCatalogSnapshot.ReviewScore> reviewScores = dataRepository.findReviewScores(
                sellableProductIds);
        Set<Long> visibleVariantIds = variants.stream().map(ProductVariant::getId).collect(Collectors.toSet());
        Map<Long, List<ProductImage>> imagesByProduct = dataRepository.findImages(sellableProductIds).stream()
                .filter(image -> image.getVariant() == null || visibleVariantIds.contains(image.getVariant().getId()))
                .collect(Collectors.groupingBy(image -> image.getProduct().getId()));

        LoadContext context = new LoadContext(categoryNames, offersByProduct, reviewScores);
        List<Item> items = sellableProducts.stream()
                .map(product -> toItem(
                        product,
                        productLocalizations.get(product.getId()),
                        categories.get(product.getCategoryId()),
                        context))
                .toList();
        Map<Long, String> campaignNames = localization.resolveCampaignNames(pricingByVariant.values(), localeCode);
        return new StorefrontCatalogSnapshot(items, imagesByProduct, campaignNames);
    }

    private Item toItem(
            Product product,
            ProductLocalization productLocalization,
            Category category,
            LoadContext context) {
        return new Item(
                product,
                productLocalization.translation(),
                productLocalization.translationLocales(),
                category,
                context.categoryNames().getOrDefault(category.getId(), category.getName()),
                context.offersByProduct().getOrDefault(product.getId(), List.of()),
                context.reviewScores().getOrDefault(product.getId(), StorefrontCatalogSnapshot.ReviewScore.EMPTY));
    }

    private record LoadContext(
            Map<Long, String> categoryNames,
            Map<Long, List<Offer>> offersByProduct,
            Map<Long, StorefrontCatalogSnapshot.ReviewScore> reviewScores
    ) {}
}
