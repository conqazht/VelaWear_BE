package vn.conganh.commercial.feature.coupon;

import java.util.List;
import vn.conganh.commercial.feature.coupon.dto.CouponResponse;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.feature.coupon.dto.UpdateCouponRequest;

public interface CouponService {

    List<CouponResponse> getAllCoupons();

    CouponResponse getCouponById(Long id);

    CouponResponse createCoupon(CreateCouponRequest request);

    CouponResponse updateCoupon(Long id, UpdateCouponRequest request);

    void deleteCoupon(Long id);
}
