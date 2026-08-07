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
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.ProductTranslation;
import vn.conganh.commercial.feature.product.ProductTranslationRepository;
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
    private final ProductTranslationRepository productTranslationRepository;
    private final SaleCampaignTranslationRepository campaignTranslationRepository;
    private final UserRepository userRepository;
    private final SaleCampaignValidator validator;
    private final SaleCampaignResponseAssembler responseAssembler;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(SaleCampaignFilterRequest filter, Pageable pageable) {
        return getAll(filter, pageable, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(
            SaleCampaignFilterRequest filter,
            Pageable pageable,
            String localeCode) {
        Instant now = Instant.now();
        var campaigns = campaignRepository
                .findAll(Specification.where(SaleCampaignSpecification.build(filter, now)), pageable);
        List<Long> campaignIds = campaigns.getContent().stream().map(SaleCampaign::getId).toList();
        List<SaleCampaign> detailedCampaigns = campaignIds.isEmpty() ? List.of() :
                campaignRepository.findAllDetailedByIdIn(campaignIds);
        Map<Long, SaleCampaignResponse> responses = responses(
                detailedCampaigns,
                now,
                localeCode);
        return ResultPaginationDTO.fromPage(campaigns.map(campaign -> responses.get(campaign.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignResponse getById(Long id) {
        return getById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignResponse getById(Long id, String localeCode) {
        return response(findDetailed(id), Instant.now(), localeCode);
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
        campaign = campaignRepository.saveAndFlush(campaign);
        upsertDefaultTranslation(campaign);
        return response(campaign, Instant.now());
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
        campaign = campaignRepository.saveAndFlush(campaign);
        upsertDefaultTranslation(campaign);
        return response(campaign, now);
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
        campaign = campaignRepository.saveAndFlush(campaign);
        upsertDefaultTranslation(campaign);
        return response(campaign, Instant.now());
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
        clone = campaignRepository.saveAndFlush(clone);
        copyTranslations(original.getId(), clone);
        return response(clone, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSalesResponse getPublic(
            SaleCampaignType type,
            List<SaleCampaignPhase> phases,
            String localeCode) {
        Instant now = Instant.now();
        List<SaleCampaign> campaignEntities = campaignRepository.findPublicCampaigns(type, now).stream()
                .filter(campaign -> phases == null
                        || phases.isEmpty()
                        || phases.contains(SaleCampaignPhase.from(
                                campaign.getStatus(),
                                campaign.getStartsAt(),
                                campaign.getEndsAt(),
                                now)))
                .toList();
        Map<Long, SaleCampaignResponse> mapped = responses(campaignEntities, now, localeCode);
        List<SaleCampaignResponse> campaigns = campaignEntities.stream()
                .map(campaign -> mapped.get(campaign.getId()))
                .toList();
        return new PublicSalesResponse(now, campaigns);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleCampaignResponse getPublicByCode(String code, String localeCode) {
        Instant now = Instant.now();
        SaleCampaign campaign = campaignRepository.findDetailedByCode(code)
                .filter(candidate -> candidate.getStatus() == SaleCampaignStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("SaleCampaign", "code", code));
        return response(campaign, now, localeCode);
    }

    private List<SaleCampaignItem> buildItems(SaleCampaignType type, List<SaleCampaignItemRequest> requests) {
        return validator.buildItems(type, requests);
    }

    private void validateForPublish(SaleCampaign campaign) {
        validator.validateForPublish(campaign);
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
        validator.validateTime(startsAt, endsAt);
    }

    private void validateUniqueCode(String code, Long currentId) {
        validator.validateUniqueCode(code, currentId);
    }

    private String normalizeCode(String code) {
        return validator.normalizeCode(code);
    }

    private void verifyVersion(SaleCampaign campaign, long expected) {
        validator.verifyVersion(campaign, expected);
    }

    private void assertLive(SaleCampaign campaign) {
        validator.assertLive(campaign);
    }

    private User findActor(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private SaleCampaignResponse response(SaleCampaign campaign, Instant now) {
        return response(campaign, now, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    private SaleCampaignResponse response(SaleCampaign campaign, Instant now, String localeCode) {
        return responses(List.of(campaign), now, localeCode).get(campaign.getId());
    }

    private Map<Long, SaleCampaignResponse> responses(
            List<SaleCampaign> campaigns,
            Instant now,
            String localeCode) {
        if (campaigns.isEmpty()) {
            return Map.of();
        }
        List<Long> campaignIds = campaigns.stream().map(SaleCampaign::getId).distinct().toList();
        List<Long> productIds = campaigns.stream()
                .flatMap(campaign -> campaign.getItems().stream())
                .map(item -> item.getVariant().getProduct().getId())
                .distinct()
                .toList();
        Map<Long, List<ProductImage>> imagesByProduct = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(image -> image.getProduct().getId()));
        Map<Long, Map<String, SaleCampaignTranslation>> campaignTranslations = campaignTranslationRepository
                .findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        SaleCampaignTranslation::getCampaignId,
                        Collectors.toMap(SaleCampaignTranslation::getLocaleCode, Function.identity())));
        Map<Long, Map<String, ProductTranslation>> productTranslations = productIds.isEmpty()
                ? Map.of()
                : productTranslationRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(
                                ProductTranslation::getProductId,
                                Collectors.toMap(ProductTranslation::getLocaleCode, Function.identity())));
        return responseAssembler.assembleResponses(
                campaigns, now, localeCode, imagesByProduct, campaignTranslations, productTranslations);
    }

    private void upsertDefaultTranslation(SaleCampaign campaign) {
        SaleCampaignTranslation translation = campaignTranslationRepository
                .findByCampaignIdAndLocaleCode(campaign.getId(), CatalogLocaleResolver.DEFAULT_LOCALE)
                .orElseGet(SaleCampaignTranslation::new);
        translation.setCampaignId(campaign.getId());
        translation.setLocaleCode(CatalogLocaleResolver.DEFAULT_LOCALE);
        translation.setName(campaign.getName());
        translation.setDescription(campaign.getDescription());
        campaignTranslationRepository.save(translation);
    }

    private void copyTranslations(Long sourceCampaignId, SaleCampaign target) {
        List<SaleCampaignTranslation> sourceTranslations = campaignTranslationRepository
                .findByCampaignId(sourceCampaignId);
        boolean hasDefault = false;
        for (SaleCampaignTranslation source : sourceTranslations) {
            SaleCampaignTranslation copy = new SaleCampaignTranslation();
            copy.setCampaignId(target.getId());
            copy.setLocaleCode(source.getLocaleCode());
            if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(source.getLocaleCode())) {
                hasDefault = true;
                copy.setName(target.getName());
                copy.setDescription(target.getDescription());
            } else {
                copy.setName(source.getName());
                copy.setDescription(source.getDescription());
            }
            campaignTranslationRepository.save(copy);
        }
        if (!hasDefault) {
            upsertDefaultTranslation(target);
        }
        campaignTranslationRepository.flush();
    }

    private int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
