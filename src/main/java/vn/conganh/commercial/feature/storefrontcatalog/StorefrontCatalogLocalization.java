package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

@Component
@RequiredArgsConstructor
class StorefrontCatalogLocalization {

    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final SaleCampaignTranslationRepository campaignTranslationRepository;

    Map<Long, ProductLocalization> resolveProducts(List<Product> products, String localeCode) {
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Map<String, ProductTranslation>> rows = productIds.isEmpty()
                ? Map.of()
                : productTranslationRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(
                                ProductTranslation::getProductId,
                                Collectors.toMap(ProductTranslation::getLocaleCode, Function.identity())));
        return products.stream().collect(Collectors.toMap(
                Product::getId,
                product -> {
                    Map<String, ProductTranslation> byLocale = rows.getOrDefault(product.getId(), Map.of());
                    return new ProductLocalization(
                            mergeProduct(product, localeCode, byLocale),
                            byLocale.keySet().stream().sorted().toList());
                }));
    }

    Map<Long, String> resolveCategoryNames(Collection<Category> categories, String localeCode) {
        List<Long> categoryIds = categories.stream().map(Category::getId).toList();
        Map<Long, Map<String, CategoryTranslation>> rows = categoryIds.isEmpty()
                ? Map.of()
                : categoryTranslationRepository.findByCategoryIdIn(categoryIds).stream()
                        .collect(Collectors.groupingBy(
                                CategoryTranslation::getCategoryId,
                                Collectors.toMap(CategoryTranslation::getLocaleCode, Function.identity())));
        return categories.stream().collect(Collectors.toMap(
                Category::getId,
                category -> resolveCategoryName(
                        category,
                        localeCode,
                        rows.getOrDefault(category.getId(), Map.of()))));
    }

    Map<Long, String> resolveCampaignNames(Collection<VariantPricing> prices, String localeCode) {
        List<Long> campaignIds = prices.stream()
                .filter(price -> price != null && price.campaignItem() != null)
                .map(price -> price.campaignItem().getCampaign().getId())
                .distinct()
                .toList();
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, SaleCampaignTranslation>> translations = campaignTranslationRepository
                .findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                                SaleCampaignTranslation::getCampaignId,
                                Collectors.toMap(SaleCampaignTranslation::getLocaleCode, Function.identity())));
        Map<Long, String> result = new HashMap<>();
        for (VariantPricing price : prices) {
            if (price == null || price.campaignItem() == null) {
                continue;
            }
            SaleCampaign campaign = price.campaignItem().getCampaign();
            Map<String, SaleCampaignTranslation> byLocale = translations.getOrDefault(
                    campaign.getId(),
                    Map.of());
            SaleCampaignTranslation requested = byLocale.get(localeCode);
            SaleCampaignTranslation fallback = byLocale.get(CatalogLocaleResolver.DEFAULT_LOCALE);
            result.put(campaign.getId(), firstValue(
                    requested == null ? null : requested.getName(),
                    fallback == null ? null : fallback.getName(),
                    campaign.getName()));
        }
        return Map.copyOf(result);
    }

    private ProductTranslation mergeProduct(
            Product product,
            String localeCode,
            Map<String, ProductTranslation> translations) {
        ProductTranslation requested = translations.get(localeCode);
        ProductTranslation fallback = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        ProductTranslation merged = new ProductTranslation();
        merged.setProductId(product.getId());
        merged.setLocaleCode(localeCode);
        merged.setName(firstValue(value(requested, ProductTranslation::getName),
                value(fallback, ProductTranslation::getName), product.getName()));
        merged.setSlug(firstValue(value(requested, ProductTranslation::getSlug),
                value(fallback, ProductTranslation::getSlug), product.getSlug()));
        merged.setShortDescription(firstValue(value(requested, ProductTranslation::getShortDescription),
                value(fallback, ProductTranslation::getShortDescription), product.getDescription()));
        merged.setDescription(firstValue(value(requested, ProductTranslation::getDescription),
                value(fallback, ProductTranslation::getDescription), product.getDescription()));
        merged.setMaterial(firstValue(value(requested, ProductTranslation::getMaterial),
                value(fallback, ProductTranslation::getMaterial)));
        merged.setCareInstruction(firstValue(value(requested, ProductTranslation::getCareInstruction),
                value(fallback, ProductTranslation::getCareInstruction)));
        merged.setSeoTitle(firstValue(value(requested, ProductTranslation::getSeoTitle),
                value(fallback, ProductTranslation::getSeoTitle), product.getName()));
        merged.setSeoDescription(firstValue(value(requested, ProductTranslation::getSeoDescription),
                value(fallback, ProductTranslation::getSeoDescription), product.getDescription()));
        return merged;
    }

    private String resolveCategoryName(
            Category category,
            String localeCode,
            Map<String, CategoryTranslation> translations) {
        CategoryTranslation requested = translations.get(localeCode);
        CategoryTranslation fallback = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        return firstValue(
                requested == null ? null : requested.getName(),
                fallback == null ? null : fallback.getName(),
                category.getName());
    }

    private <T> String value(T source, Function<T, String> extractor) {
        return source == null ? null : extractor.apply(source);
    }

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    record ProductLocalization(
            ProductTranslation translation,
            List<String> translationLocales
    ) {}
}
