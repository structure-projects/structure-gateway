package cn.structured.cloud.gateway.filter;

import cn.hutool.core.util.StrUtil;
import cn.structured.cloud.gateway.config.StructureGatewayProperties;
import cn.structured.cloud.gateway.constant.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 重放攻击防护过滤器
 * <p>
 * 职责：防止恶意用户重复发送相同请求
 * <p>
 * 防护机制：
 * 1. 时间戳检查：验证请求时间戳是否在允许的时间范围内（默认5分钟）
 * 2. Nonce检查：验证随机字符串是否已被使用过（需要Redis支持）
 * <p>
 * 如果Redis不可用，仅进行时间戳检查，跳过Nonce验证
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 40
 */
@Component
public class ReplayAttackPreventionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReplayAttackPreventionFilter.class);

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Autowired(required = false)
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getReplayCheck().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String timestampStr = request.getHeaders().getFirst(GatewayConstants.REQUEST_TIMESTAMP_HEADER);
        String signature = request.getHeaders().getFirst(GatewayConstants.REQUEST_SIGNATURE_HEADER);
        String nonce = request.getHeaders().getFirst(GatewayConstants.REQUEST_NONCE_HEADER);

        StructureGatewayProperties.ReplayCheck replayCheck = gatewayProperties.getReplayCheck();

        if (replayCheck.isRequireTimestamp() && StrUtil.isBlank(timestampStr)) {
            log.warn("Timestamp is missing for request: {}", path);
            return badRequestResponse(exchange, "X-Timestamp is required");
        }

        if (!StrUtil.isBlank(timestampStr)) {
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampStr);
                if (timestamp <= 0) {
                    log.warn("Invalid timestamp value: {} for request: {}", timestampStr, path);
                    return badRequestResponse(exchange, "X-Timestamp must be a positive number");
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid timestamp format: {} for request: {}", timestampStr, path);
                return badRequestResponse(exchange, "X-Timestamp must be a valid timestamp");
            }

            long currentTime = System.currentTimeMillis();
            long tolerance = replayCheck.getTimestampToleranceMs();

            if (Math.abs(currentTime - timestamp) > tolerance) {
                log.warn("Timestamp out of tolerance for request: {}, timestamp: {}, current: {}",
                        path, timestamp, currentTime);
                return badRequestResponse(exchange, "Request timestamp is too old or in the future");
            }
        }

        if (replayCheck.isRequireNonce() && StrUtil.isBlank(nonce)) {
            log.warn("Nonce is missing for request: {}", path);
            return badRequestResponse(exchange, "X-Nonce is required");
        }

        if (!StrUtil.isBlank(nonce)) {
            if (nonce.length() < replayCheck.getMinNonceLength() || nonce.length() > replayCheck.getMaxNonceLength()) {
                log.warn("Invalid nonce length: {} for request: {}", nonce.length(), path);
                return badRequestResponse(exchange, "X-Nonce must be between " + replayCheck.getMinNonceLength() + " and " + replayCheck.getMaxNonceLength() + " characters");
            }
        }

        if (replayCheck.isRequireSignature() && StrUtil.isBlank(signature)) {
            log.warn("Signature is missing for request: {}", path);
            return badRequestResponse(exchange, "X-Signature is required");
        }

        if (!StrUtil.isBlank(signature)) {
            if (signature.length() < replayCheck.getMinSignatureLength()) {
                log.warn("Invalid signature length: {} for request: {}", signature.length(), path);
                return badRequestResponse(exchange, "X-Signature must be at least " + replayCheck.getMinSignatureLength() + " characters");
            }
        }

        if (!StrUtil.isBlank(nonce)) {
            return checkNonce(exchange, chain, nonce);
        }

        return chain.filter(exchange);
    }

    /**
     * 检查Nonce是否已被使用
     */
    private Mono<Void> checkNonce(ServerWebExchange exchange, GatewayFilterChain chain, String nonce) {
        if (redisTemplate == null) {
            log.warn("Redis is not available, skipping nonce check");
            return chain.filter(exchange);
        }

        String nonceKey = GatewayConstants.REDIS_KEY_PREFIX_REPLAY + nonce;
        long expireMinutes = gatewayProperties.getReplayCheck().getNonceExpireMinutes();

        return redisTemplate.hasKey(nonceKey)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Nonce already used: {}", nonce);
                        return badRequestResponse(exchange, "Nonce has already been used");
                    }

                    return redisTemplate.opsForValue()
                            .set(nonceKey, "1", Duration.ofMinutes(expireMinutes))
                            .then(chain.filter(exchange));
                })
                .onErrorResume(e -> {
                    log.error("Error checking nonce: {}", nonce, e);
                    return chain.filter(exchange);
                });
    }

    /**
     * 判断路径是否在排除列表中
     */
    private boolean isExcludedPath(String path) {
        List<String> excludedPaths = gatewayProperties.getExcludedPaths();
        if (excludedPaths != null) {
            for (String excludedPath : excludedPaths) {
                if (path.startsWith(excludedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回错误请求响应
     */
    private Mono<Void> badRequestResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"code\":400,\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
