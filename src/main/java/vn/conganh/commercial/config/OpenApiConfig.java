package vn.conganh.commercial.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI commercialOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers())
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerSecurityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    private Info apiInfo() {
        return new Info()
                .title("Commercial API")
                .version("v1")
                .description("REST API for commercial management: RBAC, users, catalog, products, carts, orders, payments, and shipments.")
                .contact(new Contact()
                        .name("Commercial Team")
                        .email("support@example.com"))
                .license(new License()
                        .name("Private")
                        .url("https://example.com/license"));
    }

    private List<Server> apiServers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local development"),
                new Server()
                        .url("https://api.example.com")
                        .description("Production")
        );
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste a JWT access token. The Authorization header will be sent as: Bearer <token>.");
    }
}
