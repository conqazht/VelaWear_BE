package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.feature.user.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module SaleCampaign - CampaignReservationService")
class CampaignReservationServiceTest {

    @Mock private SaleCampaignItemRepository campaignItemRepository;
    @Mock private SaleCampaignRepository campaignRepository;
    @Mock private SaleCustomerUsageRepository customerUsageRepository;
    @Mock private SaleAllocationRepository allocationRepository;

    @InjectMocks
    private CampaignReservationService reservationService;

    private SaleCampaign campaign;
    private SaleCampaignItem campaignItem;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);

        campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 10L);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minus(1, ChronoUnit.HOURS));
        campaign.setEndsAt(Instant.now().plus(1, ChronoUnit.HOURS));

        campaignItem = new SaleCampaignItem();
        ReflectionTestUtils.setField(campaignItem, "id", 50L);
        campaignItem.setCampaign(campaign);
        campaignItem.setMaxPerCustomer(5);
    }

    @Nested
    @DisplayName("reserveFlashQuota")
    class ReserveFlashQuotaTests {
        @Test
        void reserveFlashQuota_success() {
            Instant now = Instant.now();
            when(campaignItemRepository.reserveQuota(50L, 2, now)).thenReturn(1);
            when(customerUsageRepository.reserveWithinLimit(50L, 1L, 2, 5)).thenReturn(1);

            reservationService.reserveFlashQuota(100L, campaignItem, 1L, 2, now);

            verify(customerUsageRepository).createCounterIfAbsent(50L, 1L);
            verify(campaignItemRepository).reserveQuota(50L, 2, now);
            verify(customerUsageRepository).reserveWithinLimit(50L, 1L, 2, 5);
        }

        @Test
        void reserveFlashQuota_throwsWhenSoldOut() {
            Instant now = Instant.now();
            when(campaignItemRepository.reserveQuota(50L, 2, now)).thenReturn(0);

            assertThatThrownBy(() -> reservationService.reserveFlashQuota(100L, campaignItem, 1L, 2, now))
                    .isInstanceOf(CodedBusinessException.class)
                    .satisfies(ex -> assertThat(((CodedBusinessException) ex).getCode()).isEqualTo("FLASH_SALE_SOLD_OUT"));
        }

        @Test
        void reserveFlashQuota_throwsWhenLimitExceeded() {
            Instant now = Instant.now();
            when(campaignItemRepository.reserveQuota(50L, 2, now)).thenReturn(1);
            when(customerUsageRepository.reserveWithinLimit(50L, 1L, 2, 5)).thenReturn(0);

            assertThatThrownBy(() -> reservationService.reserveFlashQuota(100L, campaignItem, 1L, 2, now))
                    .isInstanceOf(CodedBusinessException.class)
                    .satisfies(ex -> assertThat(((CodedBusinessException) ex).getCode()).isEqualTo("FLASH_SALE_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("validateAndLockStandardCampaigns")
    class StandardCampaignLockTests {
        @Test
        void validateAndLockStandardCampaigns_successWhenAllLive() {
            Instant now = Instant.now();
            when(campaignRepository.findAllStatesWithLockByIdIn(List.of(10L))).thenReturn(List.of(campaign));

            reservationService.validateAndLockStandardCampaigns(List.of(10L), now);

            verify(campaignRepository).findAllStatesWithLockByIdIn(List.of(10L));
        }

        @Test
        void validateAndLockStandardCampaigns_throwsWhenCampaignNotLive() {
            Instant now = Instant.now();
            campaign.setStatus(SaleCampaignStatus.DRAFT);
            when(campaignRepository.findAllStatesWithLockByIdIn(List.of(10L))).thenReturn(List.of(campaign));

            assertThatThrownBy(() -> reservationService.validateAndLockStandardCampaigns(List.of(10L), now))
                    .isInstanceOf(CodedBusinessException.class)
                    .satisfies(ex -> assertThat(((CodedBusinessException) ex).getCode()).isEqualTo("PRICE_CHANGED"));
        }
    }

    @Nested
    @DisplayName("confirm & release allocations")
    class AllocationLifecycleTests {
        @Test
        void confirmAllocationsForOrder_confirmsReservedAllocations() {
            SaleAllocation allocation = new SaleAllocation();
            ReflectionTestUtils.setField(allocation, "id", 100L);
            allocation.setCampaignItem(campaignItem);
            allocation.setUser(user);
            allocation.setQuantity(3);
            allocation.setStatus(SaleAllocationStatus.RESERVED);

            when(allocationRepository.findWithLockByOrderId(99L)).thenReturn(List.of(allocation));
            when(campaignItemRepository.confirmQuota(50L, 3)).thenReturn(1);
            when(customerUsageRepository.confirm(50L, 1L, 3)).thenReturn(1);

            reservationService.confirmAllocationsForOrder(99L, Instant.now());

            assertThat(allocation.getStatus()).isEqualTo(SaleAllocationStatus.CONFIRMED);
            verify(allocationRepository).flush();
        }

        @Test
        void releaseAllocationsForOrder_releasesReservedAllocations() {
            SaleAllocation allocation = new SaleAllocation();
            ReflectionTestUtils.setField(allocation, "id", 100L);
            allocation.setCampaignItem(campaignItem);
            allocation.setUser(user);
            allocation.setQuantity(3);
            allocation.setStatus(SaleAllocationStatus.RESERVED);

            when(allocationRepository.findWithLockByOrderId(99L)).thenReturn(List.of(allocation));
            when(campaignItemRepository.releaseQuota(50L, 3)).thenReturn(1);
            when(customerUsageRepository.release(50L, 1L, 3)).thenReturn(1);

            reservationService.releaseAllocationsForOrder(99L, Instant.now());

            assertThat(allocation.getStatus()).isEqualTo(SaleAllocationStatus.RELEASED);
            verify(allocationRepository).saveAll(any());
        }
    }
}
