package vn.conganh.commercial.feature.emailoutbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailNotificationProperties.class)
public class EmailOutboxConfiguration {
}
