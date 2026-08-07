package vn.conganh.commercial.feature.checkout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

@Component
public class CheckoutOrderItemAssembler {

    public List<OrderItem> assembleOrderItems(
            List<CheckoutServiceImpl.QuoteLine> lines,
            Order order,
            Function<CheckoutServiceImpl.QuoteLine, CatalogContentLocalizationService.LocalizedProduct> productLocalizer,
            Function<VariantPricing, String> campaignLocalizer,
            Function<ProductVariant, String> variantNameBuilder,
            Function<ProductVariant, String> imageResolver) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (CheckoutServiceImpl.QuoteLine line : lines) {
            ProductVariant variant = line.variant();
            VariantPricing pricing = line.pricing();
            CatalogContentLocalizationService.LocalizedProduct localizedProduct = productLocalizer.apply(line);
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariantId(variant.getId());
            orderItem.setProductName(localizedProduct.name());
            orderItem.setProductSlug(localizedProduct.slug());
            orderItem.setVariantName(variantNameBuilder.apply(variant));
            orderItem.setSku(variant.getSku());
            orderItem.setImage(imageResolver.apply(variant));
            orderItem.setListPrice(pricing.listPrice());
            orderItem.setPrice(pricing.effectivePrice());
            orderItem.setPriceSource(pricing.priceSource());
            orderItem.setSaleCampaignItem(pricing.campaignItem());
            if (pricing.campaignItem() != null) {
                orderItem.setSaleCampaignCode(pricing.campaignItem().getCampaign().getCode());
                orderItem.setSaleCampaignName(campaignLocalizer.apply(pricing));
            }
            orderItem.setQuantity(line.item().getQuantity());
            orderItem.setSubtotal(pricing.effectivePrice().multiply(BigDecimal.valueOf(line.item().getQuantity())));
            orderItem.setStatus("PENDING");
            orderItems.add(orderItem);
        }
        return orderItems;
    }
}
