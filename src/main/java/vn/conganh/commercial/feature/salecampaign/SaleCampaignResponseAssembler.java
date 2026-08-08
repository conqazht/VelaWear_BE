package vn.conganh.commercial.feature.salecampaign;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;

@Component
public class SaleCampaignResponseAssembler {

    public Map<Long, SaleCampaignResponse> assembleResponses(
            List<SaleCampaign> campaigns,
            Instant now,
            String localeCode,
            Map<Long, List<ProductImage>> imagesByProduct,
            Map<Long, Map<String, SaleCampaignTranslation>> campaignTranslations,
            Map<Long, Map<String, ProductTranslation>> productTranslations) {
        if (campaigns.isEmpty()) {
            return Map.of();
        }
        return campaigns.stream().collect(Collectors.toMap(
                SaleCampaign::getId,
                campaign -> assembleResponse(
                        campaign,
                        now,
                        localeCode,
                        imagesByProduct,
                        campaignTranslations.getOrDefault(campaign.getId(), Map.of()),
                        productTranslations)));
    }

    public SaleCampaignResponse assembleResponse(
            SaleCampaign campaign,
            Instant now,
            String localeCode,
            Map<Long, List<ProductImage>> imagesByProduct,
            Map<String, SaleCampaignTranslation> campaignTranslationsByLocale,
            Map<Long, Map<String, ProductTranslation>> productTranslations) {
        Map<Long, String> imagesByVariant = new HashMap<>();
        campaign.getItems().forEach(item -> {
            String image = resolveImage(item.getVariant(), imagesByProduct);
            if (image != null) {
                imagesByVariant.put(item.getVariant().getId(), image);
            }
        });
        SaleCampaignTranslation requestedCampaign = campaignTranslationsByLocale.get(localeCode);
        SaleCampaignTranslation defaultCampaign = campaignTranslationsByLocale.get(
                CatalogLocaleResolver.DEFAULT_LOCALE);
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> productSlugs = new HashMap<>();
        campaign.getItems().stream()
                .map(item -> item.getVariant().getProduct())
                .distinct()
                .forEach(product -> {
                    Map<String, ProductTranslation> translations = productTranslations.getOrDefault(
                            product.getId(),
                            Map.of());
                    ProductTranslation requested = translations.get(localeCode);
                    ProductTranslation defaultTranslation = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
                    productNames.put(
                            product.getId(),
                            firstValue(
                                    requested == null ? null : requested.getName(),
                                    defaultTranslation == null ? null : defaultTranslation.getName(),
                                    product.getName()));
                    productSlugs.put(
                            product.getId(),
                            firstValue(
                                    requested == null ? null : requested.getSlug(),
                                    defaultTranslation == null ? null : defaultTranslation.getSlug(),
                                    product.getSlug()));
                });

        List<String> translationLocales = campaignTranslationsByLocale.keySet().stream()
                .sorted(this::compareLocales)
                .toList();
        return SaleCampaignResponse.fromEntity(
                campaign,
                now,
                imagesByVariant,
                productNames,
                productSlugs,
                firstValue(
                        requestedCampaign == null ? null : requestedCampaign.getName(),
                        defaultCampaign == null ? null : defaultCampaign.getName(),
                        campaign.getName()),
                firstValue(
                        requestedCampaign == null ? null : requestedCampaign.getDescription(),
                        defaultCampaign == null ? null : defaultCampaign.getDescription(),
                        campaign.getDescription()),
                translationLocales);
    }

    private int compareLocales(String left, String right) {
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

    private String resolveImage(ProductVariant variant, Map<Long, List<ProductImage>> imagesByProduct) {
        List<ProductImage> images = imagesByProduct.getOrDefault(variant.getProduct().getId(), List.of());
        return images.stream()
                .filter(image -> image.getVariant() != null && image.getVariant().getId().equals(variant.getId()))
                .map(ProductImage::getImage)
                .findFirst()
                .orElseGet(() -> images.stream()
                        .filter(image -> image.getVariant() == null)
                        .sorted(Comparator.comparing(
                                ProductImage::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)))
                        .map(ProductImage::getImage)
                        .findFirst()
                        .orElse(null));
    }
}
