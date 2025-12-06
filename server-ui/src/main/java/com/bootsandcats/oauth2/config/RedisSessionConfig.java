package com.bootsandcats.oauth2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis Session Configuration for distributed session management.
 *
 * <p>This configuration enables HTTP sessions to be stored in Redis, allowing multiple application
 * pods to share session state. This is essential for horizontal scaling and high availability in
 * Kubernetes environments.
 *
 * <p>The configuration is conditionally enabled based on the {@code spring.session.store-type}
 * property. Set it to "redis" to enable Redis sessions, or "none" to use default in-memory
 * sessions.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Sessions are stored in Redis with a configurable namespace
 *   <li>Session timeout is configurable via {@code server.servlet.session.timeout}
 *   <li>JSON serialization for session attributes
 *   <li>Support for Redis standalone and cluster modes
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisHttpSession(
        maxInactiveIntervalInSeconds = 1800, // 30 minutes default, can be overridden
        redisNamespace = "oauth2:session")
public class RedisSessionConfig {

    /**
     * Configure Redis serializer for session attributes.
     *
     * <p>Uses JSON serialization for better debugging and cross-language compatibility.
     *
     * @return RedisSerializer for session values
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }

    /**
     * Configure RedisTemplate for custom Redis operations.
     *
     * <p>This template can be used for additional Redis operations beyond session management, such
     * as caching OAuth2 authorization data.
     *
     * @param connectionFactory Redis connection factory
     * @return Configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
