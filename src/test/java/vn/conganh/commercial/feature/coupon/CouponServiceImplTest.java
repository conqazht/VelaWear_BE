package vn.conganh.commercial.feature.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.dto.CouponResponse;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.feature.coupon.dto.MyCouponsResponse;
import vn.conganh.commercial.feature.coupon.dto.UpdateCouponRequest;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Coupon - CouponServiceImpl")
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @Mock
    private UserRepository userRepository;

    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponServiceImpl(couponRepository, couponUsageRepository, userRepository);
    }

    @Nested
    @DisplayName("Create coupon")
    class CreateCoupon {

        @Test
        @DisplayName("createCoupon - tạo coupon thành công khi code chưa tồn tại và ngày hợp lệ")
        void createCoupon_validRequest_returnsCouponResponse() {
            // Arrange
            CreateCouponRequest request = createRequest("SALE10", startDate(), endDate());
            when(couponRepository.existsByCode("SALE10")).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
                Coupon coupon = invocation.getArgument(0);
                ReflectionTestUtils.setField(coupon, "id", 1L);
                return coupon;
            });

            // Act
            CouponResponse response = couponService.createCoupon(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.code()).isEqualTo("SALE10");
            assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("createCoupon - không gọi save khi code đã tồn tại")
        void createCoupon_duplicateCode_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateCouponRequest request = createRequest("SALE10", startDate(), endDate());
            when(couponRepository.existsByCode("SALE10")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("createCoupon - không gọi save khi endDate không sau startDate")
        void createCoupon_invalidDateRange_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            Instant startDate = startDate();
            CreateCouponRequest request = createRequest("SALE10", startDate, startDate);
            when(couponRepository.existsByCode("SALE10")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("endDate must be after startDate");
            verify(couponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read coupon")
    class ReadCoupon {

        @Test
        @DisplayName("getAllCoupons - trả về danh sách coupon")
        void getAllCoupons_existingCoupons_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(couponRepository.findAll(ArgumentMatchers.<Specification<Coupon>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(coupon(1L, "SALE10")), pageable, 1));

            // Act
            ResultPaginationDTO responses = couponService.getAllCoupons(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("code").containsExactly("SALE10");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getCouponById - ném ResourceNotFoundException khi không tìm thấy coupon")
        void getCouponById_missingCoupon_throwsResourceNotFoundException() {
            // Arrange
            when(couponRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> couponService.getCouponById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getMyCoupons - trả coupon khả dụng và lịch sử sử dụng của đúng tài khoản")
        void getMyCoupons_existingUsage_returnsOverview() {
            User user = new User();
            ReflectionTestUtils.setField(user, "id", 7L);
            Coupon coupon = coupon(1L, "SALE10");
            Order order = new Order();
            ReflectionTestUtils.setField(order, "id", 9L);
            order.setOrderCode("VW-TEST-9");
            CouponUsage usage = new CouponUsage();
            ReflectionTestUtils.setField(usage, "id", 11L);
            usage.setCoupon(coupon);
            usage.setUser(user);
            usage.setOrder(order);
            usage.setDiscountAmount(BigDecimal.valueOf(50000));
            usage.setUsedAt(Instant.parse("2026-07-01T00:00:00Z"));

            when(userRepository.findByEmailAndDeletedAtIsNull("customer@test.local"))
                    .thenReturn(Optional.of(user));
            when(couponRepository
                    .findAllByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateAsc(
                            eq(CouponStatus.ACTIVE), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(coupon));
            when(couponUsageRepository.findAllDetailedByUserId(7L)).thenReturn(List.of(usage));

            MyCouponsResponse response = couponService.getMyCoupons("customer@test.local");

            assertThat(response.availableCoupons()).extracting(CouponResponse::code).containsExactly("SALE10");
            assertThat(response.usageHistory()).hasSize(1);
            assertThat(response.usageHistory().getFirst().orderCode()).isEqualTo("VW-TEST-9");
            assertThat(response.usageHistory().getFirst().discountAmount()).isEqualByComparingTo("50000");
        }
    }

    @Nested
    @DisplayName("Update coupon")
    class UpdateCoupon {

        @Test
        @DisplayName("updateCoupon - cập nhật coupon thành công khi id tồn tại")
        void updateCoupon_existingCoupon_returnsUpdatedResponse() {
            // Arrange
            Coupon coupon = coupon(1L, "SALE10");
            UpdateCouponRequest request = new UpdateCouponRequest(
                    CouponType.FIXED_AMOUNT,
                    BigDecimal.valueOf(30000),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(30000),
                    20,
                    startDate(),
                    endDate(),
                    CouponStatus.INACTIVE);
            when(couponRepository.findWithLockById(1L)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CouponResponse response = couponService.updateCoupon(1L, request);

            // Assert
            assertThat(response.type()).isEqualTo(CouponType.FIXED_AMOUNT);
            assertThat(response.status()).isEqualTo(CouponStatus.INACTIVE);
            verify(couponRepository).findWithLockById(1L);
            verify(couponRepository, never()).findById(1L);
        }

        @Test
        @DisplayName("updateCoupon - dùng locked lookup và ném ResourceNotFoundException khi không tìm thấy")
        void updateCoupon_missingCoupon_usesLockedLookupAndThrowsResourceNotFoundException() {
            // Arrange
            UpdateCouponRequest request = new UpdateCouponRequest(
                    CouponType.PERCENTAGE,
                    BigDecimal.TEN,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(100000),
                    10,
                    startDate(),
                    endDate(),
                    CouponStatus.ACTIVE);
            when(couponRepository.findWithLockById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> couponService.updateCoupon(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(couponRepository).findWithLockById(99L);
            verify(couponRepository, never()).findById(99L);
            verify(couponRepository, never()).save(any(Coupon.class));
        }
    }

    @Nested
    @DisplayName("Delete coupon")
    class DeleteCoupon {

        @Test
        @DisplayName("deleteCoupon - khóa coupon trước khi xóa")
        void deleteCoupon_existingCoupon_usesLockedLookup() {
            // Arrange
            Coupon coupon = coupon(1L, "SALE10");
            when(couponRepository.findWithLockById(1L)).thenReturn(Optional.of(coupon));

            // Act
            couponService.deleteCoupon(1L);

            // Assert
            verify(couponRepository).findWithLockById(1L);
            verify(couponRepository).delete(coupon);
            verify(couponRepository, never()).findById(1L);
        }
    }

    private CreateCouponRequest createRequest(String code, Instant startDate, Instant endDate) {
        return new CreateCouponRequest(
                code,
                CouponType.PERCENTAGE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100000),
                10,
                startDate,
                endDate,
                CouponStatus.ACTIVE);
    }

    private Coupon coupon(Long id, String code) {
        Coupon coupon = new Coupon();
        ReflectionTestUtils.setField(coupon, "id", id);
        coupon.setCode(code);
        coupon.setType(CouponType.PERCENTAGE);
        coupon.setValue(BigDecimal.TEN);
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setMaxDiscount(BigDecimal.valueOf(100000));
        coupon.setUsageLimit(10);
        coupon.setStartDate(startDate());
        coupon.setEndDate(endDate());
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }

    private Instant startDate() {
        return Instant.parse("2026-01-01T00:00:00Z");
    }

    private Instant endDate() {
        return Instant.parse("2026-12-31T00:00:00Z");
    }
}
