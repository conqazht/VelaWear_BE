package vn.conganh.commercial.feature.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.productvariant.ProductVariant;

@DisplayName("Module Product - ProductResponse")
class ProductResponseTest {

    @Test
    @DisplayName("fromEntity - nhóm ảnh theo màu và loại URL trùng giữa các size")
    void fromEntity_variantImages_groupsByColorAndDeduplicatesSizes() {
        Product product = product();
        Color black = color(1L, "Black", "#000000", 1);
        Color red = color(2L, "Red", "#D32F2F", 2);
        ProductVariant blackSmall = variant(11L, product, black);
        ProductVariant blackMedium = variant(12L, product, black);
        ProductVariant redLarge = variant(21L, product, red);

        ProductImage redImage = image(product, redLarge, "/red.png", true, 1);
        ProductImage blackSmallImage = image(product, blackSmall, "/black.png", true, 1);
        ProductImage blackMediumImage = image(product, blackMedium, "/black.png", true, 1);

        ProductResponse response = ProductResponse.fromEntity(
                product,
                null,
                List.of(redImage, blackMediumImage, blackSmallImage));

        assertThat(response.image()).isEqualTo("/black.png");
        assertThat(response.images()).containsExactly("/black.png");
        assertThat(response.colorImages())
                .extracting(ProductResponse.ColorImages::colorName)
                .containsExactly("Black", "Red");
        assertThat(response.colorImages().get(0).images()).containsExactly("/black.png");
        assertThat(response.colorImages().get(1).images()).containsExactly("/red.png");
    }

    private Product product() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setCategoryId(2L);
        product.setBrandId(3L);
        product.setName("Essential Cotton Tee");
        product.setSlug("essential-cotton-tee");
        product.setDescription("Description");
        product.setStatus("ACTIVE");
        return product;
    }

    private Color color(Long id, String name, String hexCode, int sortOrder) {
        Color color = new Color();
        ReflectionTestUtils.setField(color, "id", id);
        color.setName(name);
        color.setHexCode(hexCode);
        color.setSortOrder(sortOrder);
        return color;
    }

    private ProductVariant variant(Long id, Product product, Color color) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setProduct(product);
        variant.setColor(color);
        variant.setSku("SKU-" + id);
        variant.setPrice(BigDecimal.TEN);
        return variant;
    }

    private ProductImage image(
            Product product,
            ProductVariant variant,
            String path,
            boolean thumbnail,
            int sortOrder
    ) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setVariant(variant);
        image.setImage(path);
        image.setIsThumbnail(thumbnail);
        image.setSortOrder(sortOrder);
        return image;
    }
}
