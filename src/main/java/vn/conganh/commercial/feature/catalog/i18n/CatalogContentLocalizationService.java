package vn.conganh.commercial.feature.catalog.i18n;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;

@Service
@RequiredArgsConstructor
public class CatalogContentLocalizationService {

    private final ProductTranslationRepository productTranslationRepository;
    private final SaleCampaignTranslationRepository campaignTranslationRepository;

    public Map<Long, LocalizedProduct> localizeProducts(Collection<Product> products, String localeCode) {
        Map<Long, Product> productsById = products.stream()
                .filter(product -> product != null && product.getId() != null)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, ignored) -> left));
        if (productsById.isEmpty()) {
            return Map.of();
        }
        String locale = normalizedLocale(localeCode);
        Map<Long, Map<String, ProductTranslation>> translations = productTranslationRepository
                .findByProductIdIn(productsById.keySet()).stream()
                .collect(Collectors.groupingBy(
                        ProductTranslation::getProductId,
                        Collectors.toMap(ProductTranslation::getLocaleCode, Function.identity())));
        Map<Long, LocalizedProduct> localized = new HashMap<>();
        productsById.forEach((productId, product) -> {
            Map<String, ProductTranslation> byLocale = translations.getOrDefault(productId, Map.of());
            ProductTranslation requested = byLocale.get(locale);
            ProductTranslation defaultTranslation = byLocale.get(CatalogLocaleResolver.DEFAULT_LOCALE);
            localized.put(productId, new LocalizedProduct(
                    firstValue(
                            requested == null ? null : requested.getName(),
                            defaultTranslation == null ? null : defaultTranslation.getName(),
                            product.getName()),
                    firstValue(
                            requested == null ? null : requested.getSlug(),
                            defaultTranslation == null ? null : defaultTranslation.getSlug(),
                            product.getSlug())));
        });
        return localized;
    }

    public Map<Long, String> localizeCampaignNames(
            Collection<SaleCampaign> campaigns,
            String localeCode) {
        Map<Long, SaleCampaign> campaignsById = campaigns.stream()
                .filter(campaign -> campaign != null && campaign.getId() != null)
                .collect(Collectors.toMap(SaleCampaign::getId, Function.identity(), (left, ignored) -> left));
        if (campaignsById.isEmpty()) {
            return Map.of();
        }
        String locale = normalizedLocale(localeCode);
        Map<Long, Map<String, SaleCampaignTranslation>> translations = campaignTranslationRepository
                .findByCampaignIdIn(campaignsById.keySet()).stream()
                .collect(Collectors.groupingBy(
                        SaleCampaignTranslation::getCampaignId,
                        Collectors.toMap(SaleCampaignTranslation::getLocaleCode, Function.identity())));
        Map<Long, String> localized = new HashMap<>();
        campaignsById.forEach((campaignId, campaign) -> {
            Map<String, SaleCampaignTranslation> byLocale = translations.getOrDefault(campaignId, Map.of());
            SaleCampaignTranslation requested = byLocale.get(locale);
            SaleCampaignTranslation defaultTranslation = byLocale.get(CatalogLocaleResolver.DEFAULT_LOCALE);
            localized.put(campaignId, firstValue(
                    requested == null ? null : requested.getName(),
                    defaultTranslation == null ? null : defaultTranslation.getName(),
                    campaign.getName()));
        });
        return localized;
    }

    private String normalizedLocale(String localeCode) {
        return localeCode == null || localeCode.isBlank()
                ? CatalogLocaleResolver.DEFAULT_LOCALE
                : localeCode;
    }

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record LocalizedProduct(String name, String slug) {}
}
