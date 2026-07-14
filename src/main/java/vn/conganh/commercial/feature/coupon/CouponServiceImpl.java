package vn.conganh.commercial.feature.coupon;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.dto.CouponFilterRequest;
import vn.conganh.commercial.feature.coupon.dto.CouponResponse;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.feature.coupon.dto.CouponUsageResponse;
import vn.conganh.commercial.feature.coupon.dto.MyCouponsResponse;
import vn.conganh.commercial.feature.coupon.dto.UpdateCouponRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.CouponStatus;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCoupons(CouponFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(couponRepository.findAll(Specification.where(CouponSpecification.build(filter)), pageable)
                .map(CouponResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponById(Long id) {
        return CouponResponse.fromEntity(findCoupon(id));
    }

    @Override
    @Transactional(readOnly = true)
    public MyCouponsResponse getMyCoupons(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Instant now = Instant.now();
        var availableCoupons = couponRepository
                .findAllByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateAsc(
                        CouponStatus.ACTIVE, now, now)
                .stream()
                .map(CouponResponse::fromEntity)
                .toList();
        var usageHistory = couponUsageRepository.findAllDetailedByUserId(user.getId())
                .stream()
                .map(CouponUsageResponse::fromEntity)
                .toList();
        return new MyCouponsResponse(availableCoupons, usageHistory);
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        if (couponRepository.existsByCode(request.code())) {
            throw new InvalidRequestException("Coupon code already exists");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new InvalidRequestException("endDate must be after startDate");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(request.code());
        coupon.setType(request.type());
        coupon.setValue(request.value());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscount(request.maxDiscount());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStartDate(request.startDate());
        coupon.setEndDate(request.endDate());
        coupon.setStatus(request.status());
        return CouponResponse.fromEntity(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long id, UpdateCouponRequest request) {
        Coupon coupon = findCoupon(id);
        if (!request.endDate().isAfter(request.startDate())) {
            throw new InvalidRequestException("endDate must be after startDate");
        }
        coupon.setType(request.type());
        coupon.setValue(request.value());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscount(request.maxDiscount());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStartDate(request.startDate());
        coupon.setEndDate(request.endDate());
        coupon.setStatus(request.status());
        return CouponResponse.fromEntity(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        couponRepository.delete(findCoupon(id));
    }

    private Coupon findCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", id));
    }
}
