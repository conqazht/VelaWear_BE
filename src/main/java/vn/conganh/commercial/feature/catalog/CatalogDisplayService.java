package vn.conganh.commercial.feature.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;
import vn.conganh.commercial.feature.wishlist.dto.WishlistProductSummaryResponse;

@Service
@RequiredArgsConstructor
public class CatalogDisplayService {

    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantPricingService variantPricingService;
    private final SaleCampaignTranslationRepository campaignTranslationRepository;

    public ProductResponse assembleProductResponse(Product product, String localeCode) {
        return assembleProductResponses(List.of(product), localeCode).getFirst();
    }

    public List<ProductResponse> assembleProductResponses(List<Product> products, String localeCode) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).filter(Objects::nonNull).distinct().toList();
        List<Long> categoryIds = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Map<String, ProductTranslation>> translationsByProduct = loadProductTranslations(productIds);
        Map<Long, Map<String, CategoryTranslation>> translationsByCategory = loadCategoryTranslations(categoryIds);
        Map<Long, Category> categoriesById = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        Map<Long, VariantPricing> representativePrices = loadRepresentativePrices(productIds);
        Map<Long, String> campaignNames = loadCampaignNames(representativePrices.values(), localeCode);

        return products.stream().map(product -> {
            Long productId = product.getId();
            Category category = categoriesById.get(product.getCategoryId());
            Map<String, CategoryTranslation> catTrans = category == null
                    ? Map.of()
                    : translationsByCategory.getOrDefault(category.getId(), Map.of());
            CategoryTranslation mergedCat = category == null
                    ? null
                    : mergeCategoryTranslation(category, localeCode, catTrans);
            String categoryName = mergedCat == null ? null : mergedCat.getName();
            String categorySlug = mergedCat == null ? null : mergedCat.getSlug();

            Map<String, ProductTranslation> prodTrans = translationsByProduct.getOrDefault(productId, Map.of());
            ProductTranslation mergedProd = mergeProductTranslation(product, localeCode, prodTrans);

            List<ProductImage> images = imagesByProduct.getOrDefault(productId, List.of());
            VariantPricing repPricing = representativePrices.get(productId);

            return ProductResponse.fromEntity(
                    product,
                    mergedProd,
                    images,
                    categoryName,
                    categorySlug,
                    repPricing == null ? null : repPricing.listPrice(),
                    toPricingResponse(repPricing, campaignNames),
                    translationLocales(prodTrans));
        }).toList();
    }

    public Map<Long, WishlistProductSummaryResponse> assembleWishlistSummaries(
            Collection<Product> products,
            String localeCode,
            Long userId) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).filter(Objects::nonNull).distinct().toList();
        List<Long> categoryIds = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Map<String, ProductTranslation>> translationsByProduct = loadProductTranslations(productIds);
        Map<Long, Map<String, CategoryTranslation>> translationsByCategory = loadCategoryTranslations(categoryIds);
        Map<Long, Category> categoriesById = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        List<ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        Map<Long, VariantPricing> pricingByVariant = variantPricingService.resolve(variants, Instant.now(), userId);
        Map<Long, List<ProductVariant>> variantsByProduct = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        Map<Long, WishlistProductSummaryResponse> summaries = new HashMap<>();
        for (Product product : products) {
            Long pId = product.getId();
            Map<String, ProductTranslation> pTrans = translationsByProduct.getOrDefault(pId, Map.of());
            ProductTranslation pMerged = mergeProductTranslation(product, localeCode, pTrans);

            Category category = categoriesById.get(product.getCategoryId());
            Map<String, CategoryTranslation> cTrans = category == null
                    ? Map.of()
                    : translationsByCategory.getOrDefault(category.getId(), Map.of());
            CategoryTranslation cMerged = category == null
                    ? null
                    : mergeCategoryTranslation(category, localeCode, cTrans);

            List<ProductImage> imgs = imagesByProduct.getOrDefault(pId, List.of());
            String thumbnail = resolveThumbnail(null, imgs);

            BigDecimal price = null;
            VariantPricingResponse pricingResp = null;
            List<ProductVariant> pVars = variantsByProduct.getOrDefault(pId, List.of());
            if (!pVars.isEmpty()) {
                ProductVariant repVar = pVars.stream()
                        .min(Comparator.comparing((ProductVariant v) -> {
                            VariantPricing vp = pricingByVariant.get(v.getId());
                            return vp == null ? v.getPrice() : vp.effectivePrice();
                        }).thenComparing(ProductVariant::getId))
                        .orElse(pVars.getFirst());
                VariantPricing repPricing = pricingByVariant.get(repVar.getId());
                price = repVar.getPrice();
                pricingResp = repPricing == null ? null : repPricing.toResponse();
            }

            WishlistProductSummaryResponse summary = new WishlistProductSummaryResponse(
                    pId,
                    pMerged.getSlug(),
                    pMerged.getName(),
                    pMerged.getDescription(),
                    product.getCategoryId(),
                    product.getSlug(),
                    pMerged.getShortDescription(),
                    product.getStatus(),
                    thumbnail,
                    thumbnail,
                    cMerged == null ? null : cMerged.getName(),
                    cMerged == null ? null : cMerged.getSlug(),
                    price,
                    pricingResp);
            summaries.put(pId, summary);
        }
        return summaries;
    }

    public Map<Long, SaleCampaignResponse> assembleCampaignResponses(
            List<SaleCampaign> campaigns,
            Instant now,
            String localeCode) {
        if (campaigns.isEmpty()) {
            return Map.of();
        }
        List<Long> campaignIds = campaigns.stream().map(SaleCampaign::getId).distinct().toList();
        List<Long> productIds = campaigns.stream()
                .flatMap(c -> c.getItems().stream())
                .map(item -> item.getVariant().getProduct().getId())
                .distinct()
                .toList();
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(img -> img.getProduct().getId()));
        Map<Long, Map<String, SaleCampaignTranslation>> campaignTranslations = campaignTranslationRepository
                .findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        SaleCampaignTranslation::getCampaignId,
                        Collectors.toMap(SaleCampaignTranslation::getLocaleCode, Function.identity())));
        Map<Long, Map<String, ProductTranslation>> productTranslations = loadProductTranslations(productIds);

        Map<Long, SaleCampaignResponse> responses = new HashMap<>();
        for (SaleCampaign campaign : campaigns) {
            Map<Long, String> imagesByVariant = new HashMap<>();
            campaign.getItems().forEach(item -> {
                String image = resolveThumbnail(item.getVariant(), imagesByProduct.getOrDefault(item.getVariant().getProduct().getId(), List.of()));
                if (image != null) {
                    imagesByVariant.put(item.getVariant().getId(), image);
                }
            });
            Map<String, SaleCampaignTranslation> cTrans = campaignTranslations.getOrDefault(campaign.getId(), Map.of());
            SaleCampaignTranslation requestedCampaign = cTrans.get(localeCode);
            SaleCampaignTranslation defaultCampaign = cTrans.get(CatalogLocaleResolver.DEFAULT_LOCALE);

            Map<Long, String> productNames = new HashMap<>();
            Map<Long, String> productSlugs = new HashMap<>();
            campaign.getItems().stream()
                    .map(item -> item.getVariant().getProduct())
                    .distinct()
                    .forEach(product -> {
                        Map<String, ProductTranslation> pTrans = productTranslations.getOrDefault(product.getId(), Map.of());
                        ProductTranslation pMerged = mergeProductTranslation(product, localeCode, pTrans);
                        productNames.put(product.getId(), pMerged.getName());
                        productSlugs.put(product.getId(), pMerged.getSlug());
                    });

            List<String> translationLocales = cTrans.keySet().stream().sorted(this::compareLocales).toList();
            responses.put(campaign.getId(), SaleCampaignResponse.fromEntity(
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
                    translationLocales));
        }
        return responses;
    }

    public String resolveThumbnail(ProductVariant variant, List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (variant != null && variant.getId() != null) {
            String variantImg = images.stream()
                    .filter(img -> img.getVariant() != null && variant.getId().equals(img.getVariant().getId()))
                    .map(ProductImage::getImage)
                    .findFirst()
                    .orElse(null);
            if (variantImg != null) {
                return variantImg;
            }
        }
        List<ProductImage> productLevelImgs = images.stream()
                .filter(img -> img.getVariant() == null)
                .sorted(Comparator
                        .comparing((ProductImage img) -> !Boolean.TRUE.equals(img.getIsThumbnail()))
                        .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductImage::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (!productLevelImgs.isEmpty()) {
            return productLevelImgs.getFirst().getImage();
        }
        return images.getFirst().getImage();
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

    public int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private List<String> translationLocales(Map<String, ?> translations) {
        return translations.keySet().stream().sorted(this::compareLocales).toList();
    }

    private VariantPricingResponse toPricingResponse(
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

    private Map<Long, Map<String, ProductTranslation>> loadProductTranslations(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productTranslationRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(
                        ProductTranslation::getProductId,
                        Collectors.toMap(ProductTranslation::getLocaleCode, Function.identity())));
    }

    private Map<Long, Map<String, CategoryTranslation>> loadCategoryTranslations(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryTranslationRepository.findByCategoryIdIn(categoryIds).stream()
                .collect(Collectors.groupingBy(
                        CategoryTranslation::getCategoryId,
                        Collectors.toMap(CategoryTranslation::getLocaleCode, Function.identity())));
    }

    private Map<Long, VariantPricing> loadRepresentativePrices(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        Map<Long, VariantPricing> pricingByVariant = variantPricingService.resolve(variants);
        return variants.stream()
                .filter(variant -> pricingByVariant.containsKey(variant.getId()))
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(variant -> pricingByVariant.get(variant.getId()))
                                .min(Comparator
                                        .comparing(VariantPricing::effectivePrice)
                                        .thenComparing(VariantPricing::variantId))
                                .orElseThrow()));
    }

    private Map<Long, String> loadCampaignNames(
            Collection<VariantPricing> pricingValues,
            String localeCode) {
        List<Long> campaignIds = pricingValues.stream()
                .filter(pricing -> pricing != null && pricing.campaignItem() != null)
                .map(pricing -> pricing.campaignItem().getCampaign().getId())
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
        pricingValues.stream()
                .filter(pricing -> pricing != null && pricing.campaignItem() != null)
                .forEach(pricing -> {
                    var campaign = pricing.campaignItem().getCampaign();
                    Map<String, SaleCampaignTranslation> byLocale = translations.getOrDefault(
                            campaign.getId(),
                            Map.of());
                    SaleCampaignTranslation requested = byLocale.get(localeCode);
                    SaleCampaignTranslation defaultTranslation = byLocale.get(
                            CatalogLocaleResolver.DEFAULT_LOCALE);
                    result.put(
                            campaign.getId(),
                            firstValue(
                                    requested == null ? null : requested.getName(),
                                    defaultTranslation == null ? null : defaultTranslation.getName(),
                                    campaign.getName()));
                });
        return result;
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
