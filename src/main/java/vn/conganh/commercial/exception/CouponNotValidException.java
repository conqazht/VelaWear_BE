package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class CouponNotValidException extends AppException {

    public CouponNotValidException(String reason) {
        super("Coupon is not valid: " + reason, HttpStatus.BAD_REQUEST);
    }
}
