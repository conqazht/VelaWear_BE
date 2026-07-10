package vn.conganh.commercial.feature.auth.otp;

import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.util.constant.OtpPurpose;

public interface OtpService {
    void requestOtp(OtpRequest request);
    void verifyOtp(OtpVerifyRequest request);
    boolean isOtpVerified(String email, OtpPurpose purpose);
    void consumeOtpVerifiedMarker(String email, OtpPurpose purpose);
}
