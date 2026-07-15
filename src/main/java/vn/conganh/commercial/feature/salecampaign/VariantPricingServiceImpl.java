package vn.conganh.commercial.feature.salecampaign;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.security.SecurityUtil;

@Service
@RequiredArgsConstructor
public class VariantPricingServiceImpl implements VariantPricingService {

    private final SaleCampaignItemRepository itemRepository;
    private final SaleCustomerUsageRepository usageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, VariantPricing> resolve(Collection<ProductVariant> variants) {
        Long currentUserId = SecurityUtil.getCurrentUserEmail()
                .flatMap(userRepository::findByEmailAndDeletedAtIsNull)
                .map(user -> user.getId())
                .orElse(null);
        return resolve(variants, Instant.now(), currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, VariantPricing> resolve(Collection<ProductVariant> variants, Instant now, Long userId) {
        if (variants == null || variants.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (left, ignored) -> left));
        List<SaleCampaignItem> activeItems = itemRepository.findActiveForVariants(
                variantMap.keySet().stream().sorted().toList(), now);

        Map<Long, SaleCustomerUsage> usages = userId != null && !activeItems.isEmpty()
                ? usageRepository.findByCampaignItemIdInAndUserId(
                                activeItems.stream().map(SaleCampaignItem::getId).toList(), userId)
                        .stream()
                        .collect(Collectors.toMap(
                                usage -> usage.getCampaignItem().getId(), Function.identity()))
                : Map.of();

        Map<Long, List<SaleCampaignItem>> byVariant = activeItems.stream()
                .collect(Collectors.groupingBy(item -> item.getVariant().getId()));
        Map<Long, VariantPricing> result = new HashMap<>();
        for (ProductVariant variant : variantMap.values()) {
            SaleCampaignItem selected = byVariant.getOrDefault(variant.getId(), List.of()).stream()
                    .filter(item -> item.getCampaign().getType() != SaleCampaignType.FLASH || item.remainingQuota() > 0)
                    .filter(item -> isCustomerEligible(item, usages))
                    .min(Comparator
                            .comparingInt((SaleCampaignItem item) ->
                                    item.getCampaign().getType() == SaleCampaignType.FLASH ? 0 : 1)
                            .thenComparing(SaleCampaignItem::getPromotionalPrice)
                            .thenComparing(SaleCampaignItem::getId))
                    .orElse(null);
            if (selected == null) {
                result.put(variant.getId(), new VariantPricing(
                        variant.getId(), variant.getPrice(), variant.getPrice(), PriceSource.BASE,
                        null, null, null, Math.max(0, variant.getStockQuantity())));
                continue;
            }
            PriceSource source = selected.getCampaign().getType() == SaleCampaignType.FLASH
                    ? PriceSource.FLASH_SALE : PriceSource.STANDARD_SALE;
            Integer remaining = selected.getQuota() == null ? null : selected.remainingQuota();
            Integer customerRemaining = null;
            if (selected.getMaxPerCustomer() != null) {
                SaleCustomerUsage usage = usages.get(selected.getId());
                int used = usage == null ? 0 : usage.getReservedQuantity() + usage.getPurchasedQuantity();
                customerRemaining = Math.max(0, selected.getMaxPerCustomer() - used);
            }
            int available = Math.max(0, variant.getStockQuantity());
            if (remaining != null) {
                available = Math.min(available, remaining);
            }
            if (customerRemaining != null) {
                available = Math.min(available, customerRemaining);
            }
            result.put(variant.getId(), new VariantPricing(
                    variant.getId(), variant.getPrice(), selected.getPromotionalPrice(), source,
                    selected, remaining, customerRemaining, available));
        }
        return Map.copyOf(result);
    }

    private boolean isCustomerEligible(
            SaleCampaignItem item,
            Map<Long, SaleCustomerUsage> usages) {
        if (item.getCampaign().getType() != SaleCampaignType.FLASH
                || item.getMaxPerCustomer() == null) {
            return true;
        }
        SaleCustomerUsage usage = usages.get(item.getId());
        int used = usage == null ? 0 : usage.getReservedQuantity() + usage.getPurchasedQuantity();
        return used < item.getMaxPerCustomer();
    }
}
