package vn.conganh.commercial.feature.coupon;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.coupon.dto.CouponResponse;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.feature.coupon.dto.UpdateCouponRequest;

public interface CouponService {

    ResultPaginationDTO getAllCoupons(Pageable pageable);

    CouponResponse getCouponById(Long id);

    CouponResponse createCoupon(CreateCouponRequest request);

    CouponResponse updateCoupon(Long id, UpdateCouponRequest request);

    void deleteCoupon(Long id);
}
