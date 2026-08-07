package vn.conganh.commercial.feature.salecampaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignItemRequest;

@Component
@RequiredArgsConstructor
public class SaleCampaignValidator {

    private final SaleCampaignItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final SaleCampaignRepository campaignRepository;

    public List<SaleCampaignItem> buildItems(SaleCampaignType type, List<SaleCampaignItemRequest> requests) {
        List<Long> variantIds = requests.stream().map(SaleCampaignItemRequest::variantId).sorted().toList();
        if (new HashSet<>(variantIds).size() != variantIds.size()) {
            throw new InvalidRequestException("A variant can only appear once in a campaign");
        }
        List<ProductVariant> variants = variantRepository.findAllByIdsWithLock(variantIds);
        if (variants.size() != variantIds.size()) {
            throw new InvalidRequestException("One or more product variants do not exist");
        }
        lockAndValidateParentProducts(variants);
        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<SaleCampaignItem> items = new ArrayList<>();
        for (SaleCampaignItemRequest request : requests) {
            ProductVariant variant = variantMap.get(request.variantId());
            if (!"ACTIVE".equals(variant.getStatus())
                    || variant.getProduct().getDeletedAt() != null
                    || !"ACTIVE".equals(variant.getProduct().getStatus())) {
                throw new InvalidRequestException("Variant is not active: " + variant.getId());
            }
            validateItem(type, variant.getPrice(), request);
            SaleCampaignItem item = new SaleCampaignItem();
            item.setVariant(variant);
            item.setReferencePrice(variant.getPrice());
            item.setPromotionalPrice(request.promotionalPrice());
            item.setQuota(type == SaleCampaignType.FLASH ? request.quota() : null);
            item.setMaxPerCustomer(type == SaleCampaignType.FLASH ? request.maxPerCustomer() : null);
            items.add(item);
        }
        return items;
    }

    public void validateForPublish(SaleCampaign campaign) {
        Instant now = Instant.now();
        if (!campaign.getEndsAt().isAfter(now)) {
            throw new InvalidRequestException("Cannot publish an ended campaign");
        }
        if (campaign.getItems().isEmpty()) {
            throw new InvalidRequestException("Campaign must contain at least one variant");
        }
        List<Long> ids = campaign.getItems().stream().map(item -> item.getVariant().getId()).sorted().toList();
        List<ProductVariant> publishableVariants = variantRepository.findAllByIdsWithLock(ids);
        lockAndValidateParentProducts(publishableVariants);
        if (publishableVariants.size() != ids.size()
                || publishableVariants.stream().anyMatch(variant -> !"ACTIVE".equals(variant.getStatus())
                        || variant.getProduct().getDeletedAt() != null
                        || !"ACTIVE".equals(variant.getProduct().getStatus()))) {
            throw new InvalidRequestException("Campaign contains a deleted or inactive product variant");
        }
        Map<Long, List<SaleCampaignItem>> overlapsByVariant = itemRepository.findOverlappingForVariants(
                ids, campaign.getStartsAt(), campaign.getEndsAt(), campaign.getId())
            .stream()
            .collect(Collectors.groupingBy(overlap -> overlap.getVariant().getId()));
        for (SaleCampaignItem item : campaign.getItems()) {
            item.setReferencePrice(item.getVariant().getPrice());
            validateItem(campaign.getType(), item.getReferencePrice(), new SaleCampaignItemRequest(
                    item.getVariant().getId(), item.getPromotionalPrice(), item.getQuota(), item.getMaxPerCustomer()));
            for (SaleCampaignItem overlap : overlapsByVariant.getOrDefault(item.getVariant().getId(), List.of())) {
                if (overlap.getCampaign().getType() == campaign.getType()) {
                    throw rule("CAMPAIGN_OVERLAP", "Variant already belongs to an overlapping campaign of the same type",
                            Map.of("variantId", item.getVariant().getId(), "campaignId", overlap.getCampaign().getId()));
                }
                BigDecimal flashPrice = campaign.getType() == SaleCampaignType.FLASH
                        ? item.getPromotionalPrice() : overlap.getPromotionalPrice();
                BigDecimal standardPrice = campaign.getType() == SaleCampaignType.STANDARD
                        ? item.getPromotionalPrice() : overlap.getPromotionalPrice();
                if (flashPrice.compareTo(standardPrice) >= 0) {
                    throw rule("INVALID_FLASH_PRICE", "Flash price must be lower than overlapping standard price",
                            Map.of("variantId", item.getVariant().getId()));
                }
            }
        }
    }

    public void validateItem(SaleCampaignType type, BigDecimal referencePrice, SaleCampaignItemRequest item) {
        if (item.promotionalPrice().compareTo(referencePrice) >= 0) {
            throw new InvalidRequestException("Promotional price must be lower than base price for variant " + item.variantId());
        }
        if (type == SaleCampaignType.FLASH) {
            if (item.quota() == null || item.quota() < 1) {
                throw new InvalidRequestException("FLASH items require quota");
            }
            if (item.maxPerCustomer() != null && item.maxPerCustomer() > item.quota()) {
                throw new InvalidRequestException("maxPerCustomer cannot exceed quota");
            }
        } else if (item.quota() != null || item.maxPerCustomer() != null) {
            throw new InvalidRequestException("STANDARD items cannot define quota or maxPerCustomer");
        }
    }

    public void lockAndValidateParentProducts(List<ProductVariant> variants) {
        List<Long> productIds = variants.stream()
                .map(variant -> variant.getProduct().getId())
                .distinct()
                .sorted()
                .toList();
        List<Product> products = productRepository.findAllWithLockByIdIn(productIds);
        if (products.size() != productIds.size()
                || products.stream().anyMatch(product -> !"ACTIVE".equals(product.getStatus()))) {
            throw new InvalidRequestException("Campaign contains a deleted or inactive product");
        }
    }

    public void validateTime(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidRequestException("endsAt must be after startsAt");
        }
    }

    public void validateUniqueCode(String code, Long currentId) {
        campaignRepository.findDetailedByCode(normalizeCode(code)).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new InvalidRequestException("Sale campaign code already exists");
            }
        });
    }

    public String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public void verifyVersion(SaleCampaign campaign, long expected) {
        if (campaign.getVersion() != expected) {
            throw rule("CAMPAIGN_VERSION_CONFLICT", "Campaign was changed by another administrator",
                    Map.of("currentVersion", campaign.getVersion()));
        }
    }

    public void assertLive(SaleCampaign campaign) {
        Instant now = Instant.now();
        if (campaign.getStatus() != SaleCampaignStatus.PUBLISHED
                || SaleCampaignPhase.from(campaign.getStartsAt(), campaign.getEndsAt(), now) != SaleCampaignPhase.LIVE) {
            throw rule("CAMPAIGN_NOT_LIVE", "Campaign is not live");
        }
    }

    private CodedBusinessException rule(String code, String message) {
        return rule(code, message, Map.of());
    }

    private CodedBusinessException rule(String code, String message, Map<String, Object> details) {
        return new CodedBusinessException(code, message, HttpStatus.CONFLICT, details);
    }
}
