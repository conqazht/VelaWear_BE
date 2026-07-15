package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class CouponNotValidException extends CodedBusinessException {

    public CouponNotValidException(String reason) {
        super("COUPON_INVALID", "Coupon is not valid: " + reason, HttpStatus.BAD_REQUEST,
                java.util.Map.of("reason", reason));
    }
}
