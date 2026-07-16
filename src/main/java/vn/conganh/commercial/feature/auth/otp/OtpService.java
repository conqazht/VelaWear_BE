package vn.conganh.commercial.feature.auth.otp;

import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequestResponse;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyResponse;
import vn.conganh.commercial.util.constant.OtpPurpose;

public interface OtpService {

    OtpRequestResponse requestOtp(OtpRequest request);

    OtpRequestResponse requestOtp(OtpRequest request, Long actorUserId);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest request);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest request, Long actorUserId);

    void consumeProof(String proofToken, String email, OtpPurpose purpose);

    void consumeProof(String proofToken, String email, OtpPurpose purpose, Long actorUserId);
}
