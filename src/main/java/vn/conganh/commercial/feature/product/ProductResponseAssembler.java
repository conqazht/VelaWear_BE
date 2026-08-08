package vn.conganh.commercial.feature.product;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

@Component
public class ProductResponseAssembler {

    public ProductResponse assemble(
            Product product,
            String localeCode,
            Map<String, ProductTranslation> translations,
            List<ProductImage> images,
            Category category,
            Map<String, CategoryTranslation> categoryTranslations,
            VariantPricing representativePrice,
            Map<Long, String> campaignNames) {
        CategoryTranslation categoryTranslation = category == null
                ? null
                : mergeCategoryTranslation(category, localeCode, categoryTranslations);
        String categoryName = categoryTranslation == null ? null : categoryTranslation.getName();
        String categorySlug = categoryTranslation == null ? null : categoryTranslation.getSlug();

        return ProductResponse.fromEntity(
                product,
                mergeProductTranslation(product, localeCode, translations),
                images,
                categoryName,
                categorySlug,
                representativePrice == null ? null : representativePrice.listPrice(),
                toPricingResponse(representativePrice, campaignNames),
                translationLocales(translations));
    }

    public ProductTranslation mergeProductTranslation(
            Product product,
            String localeCode,
            Map<String, ProductTranslation> translations) {
        ProductTranslation requested = translations.get(localeCode);
        ProductTranslation defaultTranslation = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        ProductTranslation merged = new ProductTranslation();
        merged.setProductId(product.getId());
        merged.setLocaleCode(localeCode);
        merged.setName(firstValue(
                requested == null ? null : requested.getName(),
                defaultTranslation == null ? null : defaultTranslation.getName(),
                product.getName()));
        merged.setSlug(firstValue(
                requested == null ? null : requested.getSlug(),
                defaultTranslation == null ? null : defaultTranslation.getSlug(),
                product.getSlug()));
        merged.setShortDescription(firstValue(
                requested == null ? null : requested.getShortDescription(),
                defaultTranslation == null ? null : defaultTranslation.getShortDescription(),
                product.getDescription()));
        merged.setDescription(firstValue(
                requested == null ? null : requested.getDescription(),
                defaultTranslation == null ? null : defaultTranslation.getDescription(),
                product.getDescription()));
        merged.setMaterial(firstValue(
                requested == null ? null : requested.getMaterial(),
                defaultTranslation == null ? null : defaultTranslation.getMaterial()));
        merged.setCareInstruction(firstValue(
                requested == null ? null : requested.getCareInstruction(),
                defaultTranslation == null ? null : defaultTranslation.getCareInstruction()));
        merged.setSeoTitle(firstValue(
                requested == null ? null : requested.getSeoTitle(),
                defaultTranslation == null ? null : defaultTranslation.getSeoTitle(),
                product.getName()));
        merged.setSeoDescription(firstValue(
                requested == null ? null : requested.getSeoDescription(),
                defaultTranslation == null ? null : defaultTranslation.getSeoDescription(),
                product.getDescription()));
        return merged;
    }

    public CategoryTranslation mergeCategoryTranslation(
            Category category,
            String localeCode,
            Map<String, CategoryTranslation> translations) {
        CategoryTranslation requested = translations.get(localeCode);
        CategoryTranslation defaultTranslation = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        CategoryTranslation merged = new CategoryTranslation();
        merged.setCategoryId(category.getId());
        merged.setLocaleCode(localeCode);
        merged.setName(firstValue(
                requested == null ? null : requested.getName(),
                defaultTranslation == null ? null : defaultTranslation.getName(),
                category.getName()));
        merged.setSlug(firstValue(
                requested == null ? null : requested.getSlug(),
                defaultTranslation == null ? null : defaultTranslation.getSlug(),
                category.getSlug()));
        merged.setDescription(firstValue(
                requested == null ? null : requested.getDescription(),
                defaultTranslation == null ? null : defaultTranslation.getDescription()));
        merged.setSeoTitle(firstValue(
                requested == null ? null : requested.getSeoTitle(),
                defaultTranslation == null ? null : defaultTranslation.getSeoTitle(),
                category.getName()));
        merged.setSeoDescription(firstValue(
                requested == null ? null : requested.getSeoDescription(),
                defaultTranslation == null ? null : defaultTranslation.getSeoDescription()));
        return merged;
    }

    public VariantPricingResponse toPricingResponse(
            VariantPricing pricing,
            Map<Long, String> campaignNames) {
        if (pricing == null) {
            return null;
        }
        if (pricing.campaignItem() == null) {
            return pricing.toResponse();
        }
        Long campaignId = pricing.campaignItem().getCampaign().getId();
        return pricing.toResponse(campaignNames.getOrDefault(
                campaignId,
                pricing.campaignItem().getCampaign().getName()));
    }

    public List<String> translationLocales(Map<String, ?> translations) {
        return translations.keySet().stream().sorted(this::compareLocales).toList();
    }

    public int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
