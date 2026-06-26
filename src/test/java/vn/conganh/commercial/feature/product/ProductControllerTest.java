package vn.conganh.commercial.feature.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;

@Transactional
@DisplayName("Module Product - ProductController")
class ProductControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /products - 201: tạo product thành công khi dữ liệu hợp lệ")
        void createProduct_validRequest_returnsCreatedProduct() throws Exception {
            // Arrange
            Category category = categoryRepository.save(category("Product Category", "product-category-post"));
            Brand brand = brandRepository.save(brand("Product Brand", "product-brand-post"));
            CreateProductRequest request = validRequest(category.getId(), brand.getId(), "Test Product", "test-product-post");

            // Act & Assert
            mockMvc.perform(post("/api/v1/products")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.name", is("Test Product")))
                    .andExpect(jsonPath("$.data.slug", is("test-product-post")));

            assertThat(productRepository.existsBySlug("test-product-post")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /products - 400: từ chối khi name để trống")
        void createProduct_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            Category category = categoryRepository.save(category("Blank Product Category", "blank-product-category-post"));
            Brand brand = brandRepository.save(brand("Blank Product Brand", "blank-product-brand-post"));
            CreateProductRequest request = validRequest(category.getId(), brand.getId(), "", "blank-product-post");

            // Act & Assert
            mockMvc.perform(post("/api/v1/products")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(productRepository.existsBySlug("blank-product-post")).isFalse();
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /products - 400: từ chối khi slug đã tồn tại")
        void createProduct_duplicateSlug_returnsBadRequestAndDoesNotCreateNewProduct() throws Exception {
            // Arrange
            Category category = categoryRepository.save(category("Duplicate Product Category", "duplicate-product-category-post"));
            Brand brand = brandRepository.save(brand("Duplicate Product Brand", "duplicate-product-brand-post"));
            productRepository.save(product(category.getId(), brand.getId(), "Existing Product", "duplicate-product-post"));
            CreateProductRequest request = validRequest(category.getId(), brand.getId(), "Duplicate Product", "duplicate-product-post");
            long countBefore = productRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/products")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(productRepository.count()).isEqualTo(countBefore);
        }
    }

    private CreateProductRequest validRequest(Long categoryId, Long brandId, String name, String slug) {
        return new CreateProductRequest(categoryId, brandId, name, slug, "Integration test product", "ACTIVE");
    }

    private Product product(Long categoryId, Long brandId, String name, String slug) {
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        product.setName(name);
        product.setSlug(slug);
        product.setDescription("Existing product");
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
}
