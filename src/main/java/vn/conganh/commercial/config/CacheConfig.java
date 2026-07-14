package vn.conganh.commercial.config;

import java.time.Duration;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Cache này phục vụ tra cứu quyền RBAC; TTL dài giúp giảm đọc DB giữa các lần chủ động evict.
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // Giá trị cache chỉ chứa List<String>, tương thích giữa các app instance và DevTools restart.
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JdkSerializationRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // Flyway chạy trước Spring context sẵn sàng, nhưng Redis cache vẫn giữ giá trị cũ.
    // Evict toàn bộ permission cache mỗi lần khởi động để migration RBAC có hiệu lực ngay.
    @EventListener(ContextRefreshedEvent.class)
    @CacheEvict(value = "role_permissions", allEntries = true)
    public void evictPermissionCacheOnStartup() {
        // Cache evicted by @CacheEvict annotation
    }
}
