package vn.conganh.commercial.config;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String UPLOAD_RESOURCE_PATTERN = "/uploads/**";
    private static final String URL_SEPARATOR = "/";

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseDirectory = Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize();
        String resourceLocation = baseDirectory.toUri().toString();
        if (!resourceLocation.endsWith(URL_SEPARATOR)) {
            resourceLocation = resourceLocation + URL_SEPARATOR;
        }

        registry.addResourceHandler(UPLOAD_RESOURCE_PATTERN)
                .addResourceLocations(resourceLocation);
    }
}
