package vn.conganh.commercial.feature.productvariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.brand.Brand;
import vn.conganh.commercial.feature.brand.BrandRepository;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.color.ColorRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.size.SizeRepository;

@Transactional
@DisplayName("Module ProductVariant - ProductVariantController")
class ProductVariantControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private SizeRepository sizeRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /product-variants - 201: tạo variant thành công khi dữ liệu hợp lệ")
        void createProductVariant_validRequest_returnsCreatedVariant() throws Exception {
            // Arrange
            Product product = productRepository.save(product("Variant Product", "variant-product-post"));
            Color color = colorRepository.save(color("Variant Black"));
            Size size = sizeRepository.save(size("Variant M"));
            CreateProductVariantRequest request = validRequest(product.getId(), color.getId(), size.getId(), "SKU-POST-001");

            // Act & Assert
            mockMvc.perform(post("/api/v1/product-variants")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.sku", is("SKU-POST-001")))
                    .andExpect(jsonPath("$.data.product.id").value(product.getId()));

            assertThat(productVariantRepository.existsBySku("SKU-POST-001")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /product-variants - 400: từ chối khi sku để trống")
        void createProductVariant_blankSku_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            Product product = productRepository.save(product("Blank SKU Product", "blank-sku-product-post"));
            CreateProductVariantRequest request = validRequest(product.getId(), null, null, "");

            // Act & Assert
            mockMvc.perform(post("/api/v1/product-variants")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(productVariantRepository.findAll()).noneMatch(variant ->
                    variant.getProduct().getId().equals(product.getId()) && variant.getSku().isBlank());
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /product-variants - 400: từ chối khi sku đã tồn tại")
        void createProductVariant_duplicateSku_returnsBadRequestAndDoesNotCreateNewVariant() throws Exception {
            // Arrange
            Product product = productRepository.save(product("Duplicate SKU Product", "duplicate-sku-product-post"));
            ProductVariant existingVariant = new ProductVariant();
            existingVariant.setProduct(product);
            existingVariant.setSku("SKU-DUPLICATE-POST");
            existingVariant.setPrice(BigDecimal.valueOf(100000));
            productVariantRepository.save(existingVariant);

            CreateProductVariantRequest request = validRequest(product.getId(), null, null, "SKU-DUPLICATE-POST");
            long countBefore = productVariantRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/product-variants")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(productVariantRepository.count()).isEqualTo(countBefore);
        }
    }

    private CreateProductVariantRequest validRequest(Long productId, Long colorId, Long sizeId, String sku) {
        return new CreateProductVariantRequest(
                productId,
                sku,
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(120000),
                20,
                colorId,
                sizeId,
                "ACTIVE");
    }

    private Product product(String name, String slug) {
        Category category = categoryRepository.save(category(name + " Category", slug + "-category"));
        Brand brand = brandRepository.save(brand(name + " Brand", slug + "-brand"));
        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setBrandId(brand.getId());
        product.setName(name);
        product.setSlug(slug);
        product.setStatus("ACTIVE");
        return product;
    }

    private Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setSortOrder(1);
        category.setStatus("ACTIVE");
        return category;
    }

    private Brand brand(String name, String slug) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setStatus("ACTIVE");
        return brand;
    }

    private Color color(String name) {
        Color color = new Color();
        color.setName(name);
        color.setHexCode("#000000");
        color.setSortOrder(1);
        return color;
    }

    private Size size(String name) {
        Size size = new Size();
        size.setName(name);
        size.setSortOrder(1);
        return size;
    }
}
