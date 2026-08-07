package vn.conganh.commercial.feature.product;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

class ProductResponseAssemblerTest {

    private ProductResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ProductResponseAssembler();
    }

    @Test
    void assemble_withAllFieldsAndTranslations_returnsMappedResponse() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setName("Base Product");
        product.setSlug("base-product");
        product.setDescription("Base Description");
        product.setStatus("ACTIVE");

        ProductTranslation vi = new ProductTranslation();
        vi.setProductId(10L);
        vi.setLocaleCode("vi");
        vi.setName("Sản phẩm mẫu");
        vi.setSlug("san-pham-mau");

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 5L);
        category.setName("Base Category");
        category.setSlug("base-category");

        CategoryTranslation categoryVi = new CategoryTranslation();
        categoryVi.setCategoryId(5L);
        categoryVi.setLocaleCode("vi");
        categoryVi.setName("Danh mục Việt");
        categoryVi.setSlug("danh-muc-viet");

        VariantPricing pricing = new VariantPricing(
                100L,
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                PriceSource.BASE,
                null,
                null,
                null,
                0);

        ProductResponse response = assembler.assemble(
                product,
                "vi",
                Map.of("vi", vi),
                List.of(),
                category,
                Map.of("vi", categoryVi),
                pricing,
                Map.of());

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Sản phẩm mẫu", response.name());
        assertEquals("san-pham-mau", response.slug());
        assertEquals("Danh mục Việt", response.categoryName());
        assertEquals("danh-muc-viet", response.categorySlug());
        assertEquals(new BigDecimal("100.00"), response.price());
        assertNotNull(response.pricing());
        assertEquals(new BigDecimal("80.00"), response.pricing().effectivePrice());
        assertEquals(List.of("vi"), response.translationLocales());
    }

    @Test
    void mergeProductTranslation_fallsBackToDefaultAndProductBaseFields() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Fallback Name");
        product.setSlug("fallback-slug");
        product.setDescription("Fallback Desc");

        ProductTranslation defaultVi = new ProductTranslation();
        defaultVi.setProductId(1L);
        defaultVi.setLocaleCode("vi");
        defaultVi.setName("Vietnamese Name");
        defaultVi.setSlug("vietnamese-slug");

        ProductTranslation merged = assembler.mergeProductTranslation(product, "fr", Map.of("vi", defaultVi));

        assertEquals("Vietnamese Name", merged.getName());
        assertEquals("vietnamese-slug", merged.getSlug());
        assertEquals("Fallback Desc", merged.getShortDescription());
    }

    @Test
    void mergeCategoryTranslation_fallsBackCorrectly() {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 2L);
        category.setName("Category Base");
        category.setSlug("category-base");

        CategoryTranslation merged = assembler.mergeCategoryTranslation(category, "vi", Map.of());

        assertEquals("Category Base", merged.getName());
        assertEquals("category-base", merged.getSlug());
    }
}
