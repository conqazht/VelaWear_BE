package vn.conganh.commercial.feature.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

@DisplayName("Module Checkout - CheckoutOrderItemAssembler")
class CheckoutOrderItemAssemblerTest {

    private CheckoutOrderItemAssembler orderItemAssembler;

    @BeforeEach
    void setUp() {
        orderItemAssembler = new CheckoutOrderItemAssembler();
    }

    @Test
    @DisplayName("assembleOrderItems - assembles OrderItem snapshots accurately from QuoteLine and localizers")
    void assembleOrderItems_assemblesItemsAccurately() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Product Name");
        product.setSlug("product-name");

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 10L);
        variant.setProduct(product);
        variant.setSku("SKU-10");
        variant.setPrice(BigDecimal.valueOf(100));

        CartItem item = new CartItem();
        item.setVariantId(10L);
        item.setQuantity(3);

        VariantPricing pricing = new VariantPricing(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                PriceSource.BASE,
                null, null, null, 5);

        CheckoutServiceImpl.QuoteLine line = new CheckoutServiceImpl.QuoteLine(item, variant, pricing);

        List<OrderItem> orderItems = orderItemAssembler.assembleOrderItems(
                List.of(line),
                order,
                l -> new CatalogContentLocalizationService.LocalizedProduct("Localized Product", "localized-product"),
                p -> "Campaign Name",
                v -> "Variant Name",
                v -> "thumb.jpg");

        assertThat(orderItems).hasSize(1);
        OrderItem orderItem = orderItems.getFirst();

        assertThat(orderItem.getOrder()).isEqualTo(order);
        assertThat(orderItem.getVariantId()).isEqualTo(10L);
        assertThat(orderItem.getProductName()).isEqualTo("Localized Product");
        assertThat(orderItem.getProductSlug()).isEqualTo("localized-product");
        assertThat(orderItem.getVariantName()).isEqualTo("Variant Name");
        assertThat(orderItem.getSku()).isEqualTo("SKU-10");
        assertThat(orderItem.getImage()).isEqualTo("thumb.jpg");
        assertThat(orderItem.getListPrice()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(orderItem.getPrice()).isEqualTo(BigDecimal.valueOf(80));
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getSubtotal()).isEqualTo(BigDecimal.valueOf(240));
        assertThat(orderItem.getStatus()).isEqualTo("PENDING");
    }
}
