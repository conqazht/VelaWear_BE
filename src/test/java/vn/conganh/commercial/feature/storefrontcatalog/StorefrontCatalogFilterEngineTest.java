package vn.conganh.commercial.feature.storefrontcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Item;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Offer;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@DisplayName("Storefront catalog filter engine")
class StorefrontCatalogFilterEngineTest {

    private final StorefrontCatalogFilterEngine filterEngine = new StorefrontCatalogFilterEngine();

    @Test
    @DisplayName("Color and size selections must match the same active variant")
    void evaluate_colorAndSizeOnDifferentVariants_excludesProduct() {
        Category tops = category(1L, "tops", "Áo", 1);
        Color black = color(1L, "Black", 1);
        Color red = color(2L, "Red", 2);
        Size small = size(1L, "S", 1);
        Size medium = size(2L, "M", 2);
        Product splitProduct = product(1L, 1L, "split-product", Instant.parse("2026-07-01T00:00:00Z"));
        Product matchingProduct = product(2L, 1L, "matching-product", Instant.parse("2026-07-02T00:00:00Z"));

        Item split = item(splitProduct, tops,
                offer(11L, splitProduct, black, small, "200", "180", PriceSource.BASE),
                offer(12L, splitProduct, red, medium, "200", "180", PriceSource.BASE));
        Item matching = item(matchingProduct, tops,
                offer(21L, matchingProduct, black, medium, "220", "190", PriceSource.STANDARD_SALE));
        StorefrontProductQuery query = query(null, null, List.of(1L), List.of(2L), null, null, "featured");

        StorefrontCatalogEvaluation result = filterEngine.evaluate(snapshot(split, matching), query);

        assertThat(result.candidates())
                .extracting(candidate -> candidate.item().product().getId())
                .containsExactly(2L);
    }

    @Test
    @DisplayName("Price filters and price sorting use canonical effective price")
    void evaluate_effectivePrices_filtersAndSortsAcrossWholeResult() {
        Category tops = category(1L, "tops", "Áo", 1);
        Color black = color(1L, "Black", 1);
        Size medium = size(2L, "M", 2);
        Product flashProduct = product(1L, 1L, "flash", Instant.parse("2026-07-01T00:00:00Z"));
        Product baseProduct = product(2L, 1L, "base", Instant.parse("2026-07-03T00:00:00Z"));
        Product standardProduct = product(3L, 1L, "standard", Instant.parse("2026-07-02T00:00:00Z"));
        Item flash = item(flashProduct, tops,
                offer(11L, flashProduct, black, medium, "200", "90", PriceSource.FLASH_SALE));
        Item base = item(baseProduct, tops,
                offer(21L, baseProduct, black, medium, "120", "120", PriceSource.BASE));
        Item standard = item(standardProduct, tops,
                offer(31L, standardProduct, black, medium, "150", "110", PriceSource.STANDARD_SALE));

        StorefrontCatalogEvaluation sorted = filterEngine.evaluate(
                snapshot(flash, base, standard),
                query(null, null, null, null, null, null, "price-asc"));
        StorefrontCatalogEvaluation filtered = filterEngine.evaluate(
                snapshot(flash, base, standard),
                query(null, null, null, null, new BigDecimal("100"), new BigDecimal("115"), "price-asc"));

        assertThat(sorted.candidates())
                .extracting(candidate -> candidate.item().product().getId())
                .containsExactly(1L, 3L, 2L);
        assertThat(filtered.candidates())
                .extracting(candidate -> candidate.item().product().getId())
                .containsExactly(3L);
    }

    @Test
    @DisplayName("Featured sort prioritizes Flash then Standard then base price")
    void evaluate_featuredSort_ordersByCampaignPriority() {
        Category tops = category(1L, "tops", "Áo", 1);
        Color black = color(1L, "Black", 1);
        Size medium = size(2L, "M", 2);
        Product baseProduct = product(1L, 1L, "base", Instant.parse("2026-07-03T00:00:00Z"));
        Product standardProduct = product(2L, 1L, "standard", Instant.parse("2026-07-02T00:00:00Z"));
        Product flashProduct = product(3L, 1L, "flash", Instant.parse("2026-07-01T00:00:00Z"));
        Item base = item(baseProduct, tops,
                offer(11L, baseProduct, black, medium, "100", "100", PriceSource.BASE));
        Item standard = item(standardProduct, tops,
                offer(21L, standardProduct, black, medium, "100", "90", PriceSource.STANDARD_SALE));
        Item flash = item(flashProduct, tops,
                offer(31L, flashProduct, black, medium, "100", "95", PriceSource.FLASH_SALE));

        StorefrontCatalogEvaluation result = filterEngine.evaluate(
                snapshot(base, standard, flash),
                query(null, null, null, null, null, null, "featured"));

        assertThat(result.candidates())
                .extracting(candidate -> candidate.item().product().getId())
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("Facets keep all options and count distinct products with cross-facet filters")
    void evaluate_crossFacetCounts_returnsZeroAndPositiveOptions() {
        Category tops = category(1L, "tops", "Áo", 1);
        Category pants = category(2L, "pants", "Quần", 2);
        Color black = color(1L, "Black", 1);
        Color red = color(2L, "Red", 2);
        Size small = size(1L, "S", 1);
        Size medium = size(2L, "M", 2);
        Product first = product(1L, 1L, "first", Instant.parse("2026-07-01T00:00:00Z"));
        Product second = product(2L, 1L, "second", Instant.parse("2026-07-02T00:00:00Z"));
        Product third = product(3L, 2L, "third", Instant.parse("2026-07-03T00:00:00Z"));
        Item firstItem = item(first, tops,
                offer(11L, first, black, small, "100", "80", PriceSource.BASE),
                offer(12L, first, red, medium, "110", "90", PriceSource.BASE));
        Item secondItem = item(second, tops,
                offer(21L, second, black, medium, "120", "100", PriceSource.BASE));
        Item thirdItem = item(third, pants,
                offer(31L, third, black, medium, "130", "110", PriceSource.BASE));
        StorefrontProductQuery query = query(
                null, List.of("tops"), null, List.of(2L), null, null, "featured");

        StorefrontCatalogEvaluation result = filterEngine.evaluate(
                snapshot(firstItem, secondItem, thirdItem),
                query);

        assertThat(result.facets().categories())
                .extracting(facet -> facet.slug() + ":" + facet.count())
                .containsExactly("tops:2", "pants:1");
        assertThat(result.facets().colors())
                .extracting(facet -> facet.name() + ":" + facet.count())
                .containsExactly("Black:1", "Red:1");
        assertThat(result.facets().sizes())
                .extracting(facet -> facet.name() + ":" + facet.count())
                .containsExactly("S:1", "M:2");
        assertThat(result.facets().priceRange().min()).isEqualByComparingTo("90");
        assertThat(result.facets().priceRange().max()).isEqualByComparingTo("100");
    }

    private StorefrontCatalogSnapshot snapshot(Item... items) {
        return new StorefrontCatalogSnapshot(List.of(items), Map.of(), Map.of());
    }

    private Item item(Product product, Category category, Offer... offers) {
        ProductTranslation translation = new ProductTranslation();
        translation.setProductId(product.getId());
        translation.setLocaleCode("vi");
        translation.setName(product.getName());
        translation.setSlug(product.getSlug());
        return new Item(
                product,
                translation,
                List.of("vi"),
                category,
                category.getName(),
                List.of(offers),
                StorefrontCatalogSnapshot.ReviewScore.EMPTY);
    }

    private Offer offer(
            Long id,
            Product product,
            Color color,
            Size size,
            String listPrice,
            String effectivePrice,
            PriceSource source) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setProduct(product);
        variant.setColor(color);
        variant.setSize(size);
        variant.setPrice(new BigDecimal(listPrice));
        VariantPricing pricing = new VariantPricing(
                id,
                new BigDecimal(listPrice),
                new BigDecimal(effectivePrice),
                source,
                null,
                null,
                null,
                10);
        return new Offer(variant, pricing);
    }

    private Product product(Long id, Long categoryId, String slug, Instant createdAt) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "createdAt", createdAt);
        product.setCategoryId(categoryId);
        product.setBrandId(1L);
        product.setName(slug);
        product.setSlug(slug);
        product.setStatus("ACTIVE");
        return product;
    }

    private Category category(Long id, String slug, String name, int sortOrder) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        category.setSlug(slug);
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setStatus("ACTIVE");
        return category;
    }

    private Color color(Long id, String name, int sortOrder) {
        Color color = new Color();
        ReflectionTestUtils.setField(color, "id", id);
        color.setName(name);
        color.setSortOrder(sortOrder);
        return color;
    }

    private Size size(Long id, String name, int sortOrder) {
        Size size = new Size();
        ReflectionTestUtils.setField(size, "id", id);
        size.setName(name);
        size.setSortOrder(sortOrder);
        return size;
    }

    private StorefrontProductQuery query(
            String search,
            List<String> categories,
            List<Long> colors,
            List<Long> sizes,
            BigDecimal minimum,
            BigDecimal maximum,
            String sort) {
        return new StorefrontProductQuery(
                search, categories, colors, sizes, minimum, maximum, sort, 1, 12, "vi");
    }
}
