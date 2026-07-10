package vn.conganh.commercial.feature.auth;

import vn.conganh.commercial.feature.auth.dto.ChangeEmailRequest;
import vn.conganh.commercial.feature.auth.dto.ChangePasswordRequest;
import vn.conganh.commercial.feature.auth.dto.ForgotPasswordResetRequest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.OAuth2ExchangeRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.user.dto.UserResponse;

public interface AuthService {

    TokenResponse authenticate(LoginRequest request);

    TokenResponse authenticate(LoginRequest request, String deviceInfo, String ipAddress);

    UserResponse register(RegisterRequest request);

    TokenResponse exchangeOAuth2Code(OAuth2ExchangeRequest request, String deviceInfo, String ipAddress);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    UserResponse getMe(String email);

    void resetPassword(ForgotPasswordResetRequest request);

    void changeEmail(String currentEmail, ChangeEmailRequest request);

    void changePassword(String currentEmail, ChangePasswordRequest request);
}
