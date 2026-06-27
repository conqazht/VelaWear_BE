package vn.conganh.commercial.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class SwaggerUiController {

    @GetMapping(value = "/swagger-ui/swagger-initializer.js", produces = "text/javascript")
    public String swaggerInitializer() {
        return """
                window.onload = function () {
                    const securitySchemeName = "bearerAuth";
                    const tokenStorageKey = "velawear.swagger.accessToken";

                    window.ui = SwaggerUIBundle({
                        url: "https://petstore.swagger.io/v2/swagger.json",
                        dom_id: "#swagger-ui",
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout",
                        configUrl: "/v3/api-docs/swagger-config",
                        displayRequestDuration: true,
                        operationsSorter: "method",
                        persistAuthorization: true,
                        tagsSorter: "alpha",
                        tryItOutEnabled: true,
                        validatorUrl: "",
                        requestInterceptor: (request) => {
                            request.credentials = "include";
                            return request;
                        },
                        responseInterceptor: (response) => {
                            saveAccessTokenFromAuthResponse(response);
                            return response;
                        }
                    });

                    const storedToken = localStorage.getItem(tokenStorageKey);
                    if (storedToken) {
                        authorizeSwagger(storedToken);
                    }

                    function saveAccessTokenFromAuthResponse(response) {
                        if (!isAuthTokenEndpoint(response)) {
                            return;
                        }

                        const responseBody = parseResponseBody(response);
                        const accessToken = responseBody?.data?.accessToken;
                        if (!accessToken) {
                            return;
                        }

                        localStorage.setItem(tokenStorageKey, accessToken);
                        authorizeSwagger(accessToken);
                    }

                    function isAuthTokenEndpoint(response) {
                        if (response.status < 200 || response.status >= 300 || !response.url) {
                            return false;
                        }

                        return response.url.endsWith("/api/v1/auth/login")
                            || response.url.endsWith("/api/v1/auth/refresh");
                    }

                    function parseResponseBody(response) {
                        if (response.body) {
                            return response.body;
                        }

                        if (!response.text) {
                            return null;
                        }

                        try {
                            return JSON.parse(response.text);
                        } catch {
                            return null;
                        }
                    }

                    function authorizeSwagger(accessToken) {
                        if (!window.ui) {
                            return;
                        }

                        window.ui.preauthorizeApiKey(securitySchemeName, accessToken);
                    }
                };
                """;
    }
}
