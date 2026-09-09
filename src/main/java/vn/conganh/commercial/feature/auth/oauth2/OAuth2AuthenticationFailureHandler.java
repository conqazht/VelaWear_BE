package vn.conganh.commercial.feature.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import vn.conganh.commercial.config.OAuth2Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2Properties oauth2Properties;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        log.error("OAuth2 authentication failed at {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.frontendFailureUrl())
                .queryParam("error", "oauth2_login_failed")
                .build()
                .toUriString();
        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }
}
