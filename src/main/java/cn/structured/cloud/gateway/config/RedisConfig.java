package cn.structured.cloud.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

/**
 * Redis配置类
 * 
 * 职责：配置Redis连接和相关Bean
 * 支持响应式Redis操作
 */
@Configuration
public class RedisConfig {

    /**
     * 响应式Redis模板
     * 用于限流和重放攻击防护
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
