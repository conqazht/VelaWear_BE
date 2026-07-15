package vn.conganh.commercial.feature.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record ProductResponse(
        Long id,
        Long categoryId,
        Long brandId,
        String name,
        String slug,
        String originalSlug,
        String shortDescription,
        String description,
        String material,
        String careInstruction,
        String seoTitle,
        String seoDescription,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String image,
        String thumbnail,
        List<String> images,
        List<ColorImages> colorImages,
        String categoryName,
        String categorySlug,
        BigDecimal price,
        VariantPricingResponse pricing,
        List<String> translationLocales
) {

    public static ProductResponse fromEntity(Product product) {
        return fromEntity(product, null, null, null, null, null, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation) {
        return fromEntity(product, translation, null, null, null, null, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation, List<ProductImage> images) {
        return fromEntity(product, translation, images, null, null, null, null);
    }

    public static ProductResponse fromEntityWithTranslationLocales(
            Product product,
            ProductTranslation translation,
            List<String> translationLocales) {
        return fromEntity(product, translation, null, null, null, null, null, translationLocales);
    }

    public static ProductResponse fromEntity(
            Product product,
            ProductTranslation translation,
            List<ProductImage> images,
            String categoryName,
            String categorySlug,
            BigDecimal price,
            VariantPricingResponse pricing
    ) {
        return fromEntity(
                product,
                translation,
                images,
                categoryName,
                categorySlug,
                price,
                pricing,
                List.of());
    }

    public static ProductResponse fromEntity(
            Product product,
            ProductTranslation translation,
            List<ProductImage> images,
            String categoryName,
            String categorySlug,
            BigDecimal price,
            VariantPricingResponse pricing,
            List<String> translationLocales
    ) {
        String thumbnail = null;
        String mainImage = null;
        List<String> imagePaths = java.util.Collections.emptyList();
        List<ColorImages> colorImageGroups = java.util.Collections.emptyList();
        if (images != null && !images.isEmpty()) {
            List<ProductImage> productLevelImages = images.stream()
                    .filter(image -> image.getVariant() == null)
                    .sorted(imageComparator())
                    .toList();
            colorImageGroups = groupImagesByColor(images);

            imagePaths = productLevelImages.stream()
                    .map(ProductImage::getImage)
                    .distinct()
                    .toList();
            thumbnail = productLevelImages.stream()
                    .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                    .map(ProductImage::getImage)
                    .findFirst()
                    .orElse(imagePaths.isEmpty() ? null : imagePaths.get(0));

            if (imagePaths.isEmpty() && !colorImageGroups.isEmpty()) {
                ColorImages defaultColorImages = colorImageGroups.get(0);
                imagePaths = defaultColorImages.images();
                thumbnail = defaultColorImages.thumbnail();
            }
            mainImage = thumbnail;
        }

        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getBrandId(),
                value(translation == null ? null : translation.getName(), product.getName()),
                value(translation == null ? null : translation.getSlug(), product.getSlug()),
                product.getSlug(),
                translation == null ? product.getDescription() : translation.getShortDescription(),
                value(translation == null ? null : translation.getDescription(), product.getDescription()),
                translation == null ? null : translation.getMaterial(),
                translation == null ? null : translation.getCareInstruction(),
                value(translation == null ? null : translation.getSeoTitle(), product.getName()),
                translation == null ? product.getDescription() : translation.getSeoDescription(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                mainImage,
                thumbnail,
                imagePaths,
                colorImageGroups,
                categoryName,
                categorySlug,
                price,
                pricing,
                translationLocales);
    }

    private static List<ColorImages> groupImagesByColor(List<ProductImage> images) {
        Map<Long, List<ProductImage>> imagesByColor = new LinkedHashMap<>();
        images.stream()
                .filter(image -> image.getVariant() != null && image.getVariant().getColor() != null)
                .sorted(colorImageComparator())
                .forEach(image -> imagesByColor
                        .computeIfAbsent(image.getVariant().getColor().getId(), ignored -> new java.util.ArrayList<>())
                        .add(image));

        return imagesByColor.values().stream()
                .map(group -> {
                    Color color = group.get(0).getVariant().getColor();
                    List<String> paths = group.stream()
                            .sorted(imageComparator())
                            .map(ProductImage::getImage)
                            .distinct()
                            .toList();
                    String colorThumbnail = group.stream()
                            .sorted(imageComparator())
                            .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                            .map(ProductImage::getImage)
                            .findFirst()
                            .orElse(paths.get(0));
                    return new ColorImages(
                            color.getId(),
                            color.getName(),
                            color.getHexCode(),
                            colorThumbnail,
                            paths);
                })
                .toList();
    }

    private static Comparator<ProductImage> colorImageComparator() {
        return Comparator
                .comparing(
                        (ProductImage image) -> image.getVariant().getColor().getSortOrder(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        image -> image.getVariant().getColor().getName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(imageComparator());
    }

    private static Comparator<ProductImage> imageComparator() {
        return Comparator.comparing(
                ProductImage::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public record ColorImages(
            Long colorId,
            String colorName,
            String hexCode,
            String thumbnail,
            List<String> images
    ) {}

    private static String value(String translated, String fallback) {
        return translated == null || translated.isBlank() ? fallback : translated;
    }
}
