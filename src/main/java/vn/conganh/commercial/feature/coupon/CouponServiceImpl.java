package vn.conganh.commercial.feature.coupon;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.coupon.dto.CouponResponse;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.feature.coupon.dto.UpdateCouponRequest;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream().map(CouponResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponById(Long id) {
        return CouponResponse.fromEntity(findCoupon(id));
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
