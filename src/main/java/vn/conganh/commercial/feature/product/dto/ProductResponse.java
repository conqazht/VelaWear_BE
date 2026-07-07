package vn.conganh.commercial.feature.product.dto;
import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductTranslation;

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
        String categoryName,
        String categorySlug
) {

    public static ProductResponse fromEntity(Product product) {
        return fromEntity(product, null, null, null, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation) {
        return fromEntity(product, translation, null, null, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation, List<ProductImage> images) {
        return fromEntity(product, translation, images, null, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation, List<ProductImage> images, String categoryName, String categorySlug) {
        String thumbnail = null;
        String mainImage = null;
        List<String> imagePaths = java.util.Collections.emptyList();
        if (images != null && !images.isEmpty()) {
            List<ProductImage> sorted = images.stream()
                    .sorted(java.util.Comparator.comparing(ProductImage::getSortOrder))
                    .toList();
            imagePaths = sorted.stream().map(ProductImage::getImage).toList();
            thumbnail = sorted.stream()
                    .filter(ProductImage::getIsThumbnail)
                    .map(ProductImage::getImage)
                    .findFirst()
                    .orElse(imagePaths.get(0));
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
                categoryName,
                categorySlug);
    }

    private static String value(String translated, String fallback) {
        return translated == null || translated.isBlank() ? fallback : translated;
    }
}
