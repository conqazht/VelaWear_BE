package vn.conganh.commercial.feature.salecampaign;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.CodedBusinessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignReservationService {

    private final SaleCampaignItemRepository campaignItemRepository;
    private final SaleCampaignRepository campaignRepository;
    private final SaleCustomerUsageRepository customerUsageRepository;
    private final SaleAllocationRepository allocationRepository;

    public void reserveFlashQuota(
            Long variantId,
            SaleCampaignItem campaignItem,
            Long userId,
            int quantity,
            Instant now) {
        customerUsageRepository.createCounterIfAbsent(campaignItem.getId(), userId);
        int max = campaignItem.getMaxPerCustomer() == null ? Integer.MAX_VALUE : campaignItem.getMaxPerCustomer();
        if (campaignItemRepository.reserveQuota(campaignItem.getId(), quantity, now) != 1) {
            String code = (campaignItem.getCampaign() != null
                    && campaignItem.getCampaign().getEndsAt() != null
                    && !campaignItem.getCampaign().getEndsAt().isAfter(now))
                    ? "FLASH_SALE_ENDED"
                    : "FLASH_SALE_SOLD_OUT";
            throw new CodedBusinessException(
                    code,
                    "Flash sale quota is no longer available",
                    HttpStatus.CONFLICT,
                    Map.of("variantId", variantId));
        }
        if (customerUsageRepository.reserveWithinLimit(
                campaignItem.getId(), userId, quantity, max) != 1) {
            throw new CodedBusinessException(
                    "FLASH_SALE_LIMIT_EXCEEDED",
                    "Flash sale customer limit exceeded",
                    HttpStatus.CONFLICT,
                    Map.of("variantId", variantId, "maxPerCustomer", max));
        }
    }

    public void validateAndLockStandardCampaigns(List<Long> standardCampaignIds, Instant lockedAt) {
        if (standardCampaignIds == null || standardCampaignIds.isEmpty()) {
            return;
        }
        List<SaleCampaign> lockedCampaigns = campaignRepository.findAllStatesWithLockByIdIn(standardCampaignIds);
        boolean stillLive = lockedCampaigns.size() == standardCampaignIds.size()
                && lockedCampaigns.stream().allMatch(campaign ->
                        campaign.getStatus() == SaleCampaignStatus.PUBLISHED
                                && (campaign.getStartsAt() == null || !lockedAt.isBefore(campaign.getStartsAt()))
                                && (campaign.getEndsAt() == null || lockedAt.isBefore(campaign.getEndsAt())));
        if (!stillLive) {
            throw new CodedBusinessException(
                    "PRICE_CHANGED",
                    "A scheduled sale ended while checkout was being confirmed",
                    HttpStatus.CONFLICT,
                    Map.of());
        }
    }

    public void saveAllocations(List<SaleAllocation> allocations) {
        if (allocations != null && !allocations.isEmpty()) {
            allocationRepository.saveAll(allocations);
            allocationRepository.flush();
        }
    }

    public void confirmAllocationsForOrder(Long orderId, Instant now) {
        for (SaleAllocation allocation : allocationRepository.findWithLockByOrderId(orderId)) {
            if (allocation.getStatus() != SaleAllocationStatus.RESERVED) {
                continue;
            }
            int quantity = allocation.getQuantity();
            Long itemId = allocation.getCampaignItem().getId();
            Long userId = allocation.getUser().getId();
            if (campaignItemRepository.confirmQuota(itemId, quantity) != 1
                    || customerUsageRepository.confirm(itemId, userId, quantity) != 1) {
                throw new IllegalStateException("Cannot confirm flash allocation " + allocation.getId());
            }
            allocation.setStatus(SaleAllocationStatus.CONFIRMED);
            allocation.setConfirmedAt(now);
        }
        allocationRepository.flush();
    }

    public void releaseAllocationsForOrder(Long orderId, Instant now) {
        List<SaleAllocation> allocations = allocationRepository.findWithLockByOrderId(orderId);
        for (SaleAllocation allocation : allocations) {
            int quantity = allocation.getQuantity();
            Long itemId = allocation.getCampaignItem().getId();
            Long userId = allocation.getUser().getId();
            if (allocation.getStatus() == SaleAllocationStatus.RESERVED) {
                if (campaignItemRepository.releaseQuota(itemId, quantity) != 1
                        || customerUsageRepository.release(itemId, userId, quantity) != 1) {
                    throw new IllegalStateException("Cannot release flash allocation " + allocation.getId());
                }
                allocation.setStatus(SaleAllocationStatus.RELEASED);
                allocation.setReleasedAt(now);
            } else if (allocation.getStatus() == SaleAllocationStatus.CONFIRMED) {
                if (campaignItemRepository.reverseSoldQuota(itemId, quantity) != 1
                        || customerUsageRepository.reverse(itemId, userId, quantity) != 1) {
                    throw new IllegalStateException("Cannot reverse flash allocation " + allocation.getId());
                }
                allocation.setStatus(SaleAllocationStatus.REVERSED);
                allocation.setReversedAt(now);
            }
        }
        allocationRepository.saveAll(allocations);
    }
}
