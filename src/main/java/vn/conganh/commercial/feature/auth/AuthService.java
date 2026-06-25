package vn.conganh.commercial.feature.auth;

import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;

public interface AuthService {

    TokenResponse authenticate(LoginRequest request);
}
