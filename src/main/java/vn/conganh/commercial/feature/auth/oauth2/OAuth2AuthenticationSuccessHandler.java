package vn.conganh.commercial.feature.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import vn.conganh.commercial.config.OAuth2Properties;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.user.User;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2GoogleAccountService googleAccountService;
    private final OAuth2LoginCodeService loginCodeService;
    private final OAuth2Properties oauth2Properties;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        if (!(authentication instanceof OAuth2AuthenticationToken oauth2AuthenticationToken)) {
            throw new InvalidRequestException("OAuth2 authentication token is invalid");
        }

        OAuth2User oauth2User = oauth2AuthenticationToken.getPrincipal();
        User user = googleAccountService.resolveOrCreateUser(oauth2User);
        String code = loginCodeService.create(user.getId());
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.frontendSuccessUrl())
                .queryParam("code", code)
                .build()
                .toUriString();

        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }
}
