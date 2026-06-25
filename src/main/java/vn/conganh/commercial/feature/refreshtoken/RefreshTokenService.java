package vn.conganh.commercial.feature.refreshtoken;

import java.util.List;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.refreshtoken.dto.RefreshTokenResponse;

public interface RefreshTokenService {

    List<RefreshTokenResponse> getAllRefreshTokens();

    RefreshTokenResponse getRefreshTokenById(Long id);

    RefreshTokenResponse createRefreshToken(CreateRefreshTokenRequest request);

    RefreshTokenResponse revokeRefreshToken(Long id);

    void deleteRefreshToken(Long id);
}
