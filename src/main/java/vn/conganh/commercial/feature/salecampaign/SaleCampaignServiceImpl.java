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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.product.ProductImage;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.salecampaign.dto.CreateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.EndAndCloneSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.IncreaseQuotaRequest;
import vn.conganh.commercial.feature.salecampaign.dto.PublicSalesResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignFilterRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignItemRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleDisplayRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class SaleCampaignServiceImpl implements SaleCampaignService {

    private final SaleCampaignRepository campaignRepository;
    private final SaleCampaignItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(SaleCampaignFilterRequest filter, Pageable pageable) {
        Instant now = Instant.now();
        return ResultPaginationDTO.fromPage(campaignRepository
                .findAll(Specification.where(SaleCampaignSpecification.build(filter, now)), pageable)
                .map(campaign -> response(campaign, now)));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignResponse getById(Long id) {
        return response(findDetailed(id), Instant.now());
    }

    @Override
    @Transactional
    public SaleCampaignResponse create(CreateSaleCampaignRequest request, String actorEmail) {
        validateTime(request.startsAt(), request.endsAt());
        validateUniqueCode(request.code(), null);
        SaleCampaign campaign = new SaleCampaign();
        campaign.setCreatedBy(findActor(actorEmail));
        applyCampaign(campaign, request.code(), request.name(), request.description(), request.bannerUrl(),
                request.type(), request.startsAt(), request.endsAt());
        campaign.replaceItems(buildItems(request.type(), request.items()));
        return response(campaignRepository.saveAndFlush(campaign), Instant.now());
    }

    @Override
    @Transactional
    public SaleCampaignResponse update(Long id, UpdateSaleCampaignRequest request) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, request.version());
        Instant now = Instant.now();
        boolean editable = campaign.getStatus() == SaleCampaignStatus.DRAFT
                || (campaign.getStatus() == SaleCampaignStatus.PUBLISHED && now.isBefore(campaign.getStartsAt()));
        if (!editable) {
            throw rule("CAMPAIGN_ALREADY_STARTED", "Campaign cannot be fully edited after it starts");
        }
        validateTime(request.startsAt(), request.endsAt());
        validateUniqueCode(request.code(), id);
        applyCampaign(campaign, request.code(), request.name(), request.description(), request.bannerUrl(),
                request.type(), request.startsAt(), request.endsAt());
        campaign.replaceItems(buildItems(request.type(), request.items()));
        if (campaign.getStatus() == SaleCampaignStatus.PUBLISHED) {
            validateForPublish(campaign);
        }
        return response(campaignRepository.saveAndFlush(campaign), now);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SaleCampaign campaign = findLocked(id);
        if (campaign.getStatus() != SaleCampaignStatus.DRAFT) {
            throw new InvalidRequestException("Only a DRAFT campaign can be deleted");
        }
        campaignRepository.delete(campaign);
    }

    @Override
    @Transactional
    public SaleCampaignResponse publish(Long id, long version, String actorEmail) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, version);
        if (campaign.getStatus() != SaleCampaignStatus.DRAFT) {
            throw new InvalidRequestException("Only a DRAFT campaign can be published");
        }
        validateForPublish(campaign);
        Instant now = Instant.now();
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setPublishedAt(now);
        campaign.setPublishedBy(findActor(actorEmail));
        return response(campaignRepository.saveAndFlush(campaign), now);
    }

    @Override
    @Transactional
    public SaleCampaignResponse cancel(Long id, long version) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, version);
        Instant now = Instant.now();
        if (campaign.getStatus() != SaleCampaignStatus.PUBLISHED || !now.isBefore(campaign.getStartsAt())) {
            throw rule("CAMPAIGN_ALREADY_STARTED", "Only an upcoming published campaign can be cancelled");
        }
        campaign.setStatus(SaleCampaignStatus.CANCELLED);
        campaign.setCancelledAt(now);
        return response(campaignRepository.saveAndFlush(campaign), now);
    }

    @Override
    @Transactional
    public SaleCampaignResponse updateDisplay(Long id, UpdateSaleDisplayRequest request) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, request.version());
        assertLive(campaign);
        campaign.setName(request.name().trim());
        campaign.setDescription(request.description());
        campaign.setBannerUrl(request.bannerUrl());
        return response(campaignRepository.saveAndFlush(campaign), Instant.now());
    }

    @Override
    @Transactional
    public SaleCampaignResponse increaseQuota(Long id, Long itemId, IncreaseQuotaRequest request) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, request.version());
        assertLive(campaign);
        if (campaign.getType() != SaleCampaignType.FLASH) {
            throw new InvalidRequestException("Only a FLASH campaign has quota");
        }
        SaleCampaignItem lockedItem = itemRepository.findWithLockById(itemId)
                .filter(candidate -> candidate.getCampaign().getId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaignItem", "id", itemId));
        if (lockedItem.getQuota() == null) {
            throw new InvalidRequestException("Flash campaign item has no quota");
        }
        if (itemRepository.increaseQuota(id, itemId, request.additionalQuantity()) != 1
                || campaignRepository.bumpVersion(id, request.version()) != 1) {
            throw rule("CAMPAIGN_VERSION_CONFLICT", "Campaign or quota was changed concurrently");
        }
        return response(findDetailed(id), Instant.now());
    }

    @Override
    @Transactional
    public SaleCampaignResponse end(Long id, long version) {
        SaleCampaign campaign = findLocked(id);
        verifyVersion(campaign, version);
        assertLive(campaign);
        Instant now = Instant.now();
        campaign.setEndsAt(now);
        return response(campaignRepository.saveAndFlush(campaign), now);
    }

    @Override
    @Transactional
    public SaleCampaignResponse endAndClone(Long id, EndAndCloneSaleCampaignRequest request, String actorEmail) {
        SaleCampaign original = findLocked(id);
        verifyVersion(original, request.version());
        assertLive(original);
        validateTime(request.startsAt(), request.endsAt());
        validateUniqueCode(request.code(), null);
        original.setEndsAt(Instant.now());
        campaignRepository.saveAndFlush(original);

        SaleCampaign clone = new SaleCampaign();
        clone.setCreatedBy(findActor(actorEmail));
        applyCampaign(clone, request.code(), request.name(), original.getDescription(), original.getBannerUrl(),
                original.getType(), request.startsAt(), request.endsAt());
        List<SaleCampaignItem> clonedItems = original.getItems().stream().map(source -> {
            SaleCampaignItem item = new SaleCampaignItem();
            item.setVariant(source.getVariant());
            item.setReferencePrice(source.getVariant().getPrice());
            item.setPromotionalPrice(source.getPromotionalPrice());
            item.setQuota(source.getQuota());
            item.setMaxPerCustomer(source.getMaxPerCustomer());
            return item;
        }).toList();
        clone.replaceItems(clonedItems);
        return response(campaignRepository.saveAndFlush(clone), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSalesResponse getPublic(SaleCampaignType type, List<SaleCampaignPhase> phases) {
        Instant now = Instant.now();
        List<SaleCampaignResponse> campaigns = campaignRepository.findPublicCampaigns(type, now).stream()
                .map(campaign -> response(campaign, now))
                .filter(campaign -> phases == null || phases.isEmpty() || phases.contains(campaign.phase()))
                .toList();
        return new PublicSalesResponse(now, campaigns);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignResponse getPublicByCode(String code) {
        Instant now = Instant.now();
        SaleCampaign campaign = campaignRepository.findDetailedByCode(code)
                .filter(candidate -> candidate.getStatus() == SaleCampaignStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "code", code));
        return response(campaign, now);
    }

    private List<SaleCampaignItem> buildItems(SaleCampaignType type, List<SaleCampaignItemRequest> requests) {
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

    private void validateForPublish(SaleCampaign campaign) {
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
        for (SaleCampaignItem item : campaign.getItems()) {
            item.setReferencePrice(item.getVariant().getPrice());
            validateItem(campaign.getType(), item.getReferencePrice(), new SaleCampaignItemRequest(
                    item.getVariant().getId(), item.getPromotionalPrice(), item.getQuota(), item.getMaxPerCustomer()));
            for (SaleCampaignItem overlap : itemRepository.findOverlappingForVariant(
                    item.getVariant().getId(), campaign.getStartsAt(), campaign.getEndsAt(), campaign.getId())) {
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

    private void validateItem(SaleCampaignType type, BigDecimal referencePrice, SaleCampaignItemRequest item) {
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

    private void lockAndValidateParentProducts(List<ProductVariant> variants) {
        List<Long> productIds = variants.stream()
                .map(variant -> variant.getProduct().getId())
                .distinct()
                .sorted()
                .toList();
        List<vn.conganh.commercial.feature.product.Product> products =
                productRepository.findAllWithLockByIdIn(productIds);
        if (products.size() != productIds.size()
                || products.stream().anyMatch(product -> !"ACTIVE".equals(product.getStatus()))) {
            throw new InvalidRequestException("Campaign contains a deleted or inactive product");
        }
    }

    private void applyCampaign(SaleCampaign campaign, String code, String name, String description, String bannerUrl,
                               SaleCampaignType type, Instant startsAt, Instant endsAt) {
        campaign.setCode(normalizeCode(code));
        campaign.setName(name.trim());
        campaign.setDescription(description);
        campaign.setBannerUrl(bannerUrl);
        campaign.setType(type);
        campaign.setStartsAt(startsAt);
        campaign.setEndsAt(endsAt);
    }

    private void validateTime(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidRequestException("endsAt must be after startsAt");
        }
    }

    private void validateUniqueCode(String code, Long currentId) {
        campaignRepository.findDetailedByCode(normalizeCode(code)).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new InvalidRequestException("Sale campaign code already exists");
            }
        });
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void verifyVersion(SaleCampaign campaign, long expected) {
        if (campaign.getVersion() != expected) {
            throw rule("CAMPAIGN_VERSION_CONFLICT", "Campaign was changed by another administrator",
                    Map.of("currentVersion", campaign.getVersion()));
        }
    }

    private void assertLive(SaleCampaign campaign) {
        Instant now = Instant.now();
        if (campaign.getStatus() != SaleCampaignStatus.PUBLISHED
                || SaleCampaignPhase.from(campaign.getStartsAt(), campaign.getEndsAt(), now) != SaleCampaignPhase.LIVE) {
            throw rule("CAMPAIGN_NOT_LIVE", "Campaign is not live");
        }
    }

    private User findActor(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private SaleCampaignResponse response(SaleCampaign campaign, Instant now) {
        List<Long> productIds = campaign.getItems().stream()
                .map(item -> item.getVariant().getProduct().getId())
                .distinct()
                .toList();
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(image -> image.getProduct().getId()));
        Map<Long, String> imagesByVariant = new java.util.HashMap<>();
        campaign.getItems().forEach(item -> {
            String image = resolveImage(item.getVariant(), imagesByProduct);
            if (image != null) {
                imagesByVariant.put(item.getVariant().getId(), image);
            }
        });
        return SaleCampaignResponse.fromEntity(campaign, now, imagesByVariant);
    }

    private String resolveImage(ProductVariant variant, Map<Long, List<ProductImage>> imagesByProduct) {
        List<ProductImage> images = imagesByProduct.getOrDefault(variant.getProduct().getId(), List.of());
        return images.stream()
                .filter(image -> image.getVariant() != null && image.getVariant().getId().equals(variant.getId()))
                .map(ProductImage::getImage)
                .findFirst()
                .orElseGet(() -> images.stream()
                        .filter(image -> image.getVariant() == null)
                        .sorted(java.util.Comparator.comparing(
                                ProductImage::getSortOrder,
                                java.util.Comparator.nullsLast(Integer::compareTo)))
                        .map(ProductImage::getImage)
                        .findFirst()
                        .orElse(null));
    }

    private SaleCampaign findDetailed(Long id) {
        return campaignRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "id", id));
    }

    private SaleCampaign findLocked(Long id) {
        return campaignRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "id", id));
    }

    private CodedBusinessException rule(String code, String message) {
        return rule(code, message, Map.of());
    }

    private CodedBusinessException rule(String code, String message, Map<String, Object> details) {
        return new CodedBusinessException(code, message, HttpStatus.CONFLICT, details);
    }
}
