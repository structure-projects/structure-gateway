package cn.structured.cloud.gateway.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 租户限流过滤器
 * <p>
 * 职责：根据租户套餐进行限流控制
 * 执行顺序：
 * 1. 检查是否在白名单，白名单租户直接放行
 * 2. 从Redis读取租户套餐信息，如果没有则直接拒绝
 * 3. 根据Redis中读取到的限流配置执行限流检查
 * <p>
 * 限流维度：
 * - QPS限流：每秒请求数
 * - 日限流：每天请求总数
 * - 月限流：每月请求总数
 * <p>
 * 所有限流逻辑仅依赖Redis，不使用本地YAML配置
 */
@Component
public class TenantRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantRateLimitFilter.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Autowired(required = false)
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String tenantId = request.getHeaders().getFirst(GatewayConstants.TENANT_ID_HEADER);

        if (StrUtil.isBlank(tenantId)) {
            tenantId = gatewayProperties.getTenantIdentification().getDefaultTenantId();
        }

        // 步骤1：检查是否是白名单租户，白名单直接放行
        if (isWhitelistTenant(tenantId)) {
            log.debug("Whitelist tenant, skipping rate limit for: {}", tenantId);
            return chain.filter(exchange);
        }

        // 步骤2：检查Redis是否可用
        if (redisTemplate == null) {
            log.warn("Redis is not available, skipping rate limit for tenant: {}", tenantId);
            return chain.filter(exchange);
        }

        // 步骤3：从Redis读取套餐信息
        String packageKey = GatewayConstants.REDIS_KEY_PREFIX_TENANT_PACKAGE + tenantId;

        final String finalTenantId = tenantId;
        final ServerWebExchange finalExchange = exchange;
        final GatewayFilterChain finalChain = chain;

        return redisTemplate.opsForValue()
                .get(packageKey)
                .flatMap(packageJson -> {
                    try {
                        log.debug("Found package info for tenant {}: {}", finalTenantId, packageJson);
                        JSONObject packageInfo = JSONUtil.parseObj(packageJson);

                        // 检查是否启限流
                        Boolean rateLimitEnabled = packageInfo.getBool("rateLimitEnabled");
                        if (rateLimitEnabled != null && !rateLimitEnabled) {
                            log.debug("Rate limit is disabled for tenant: {}", finalTenantId);
                            return finalChain.filter(finalExchange);
                        }

                        // 步骤4：读取限流配置并执行限流检查
                        return doRateLimitCheck(finalExchange, finalChain, finalTenantId, packageInfo);
                    } catch (Exception e) {
                        log.error("Error parsing package info for tenant: {}, rejecting request", finalTenantId, e);
                        return tenantNotFoundResponse(finalExchange);
                    }
                })
                // 步骤5：Redis中没有套餐信息，拒绝请求
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No package info found for tenant: {}, rejecting request", finalTenantId);
                    return tenantNotFoundResponse(finalExchange);
                }))
                .onErrorResume(e -> {
                    log.error("Error checking rate limit for tenant: {}, rejecting request", finalTenantId, e);
                    return tenantNotFoundResponse(finalExchange);
                });
    }

    /**
     * 检查租户是否是白名单租户
     */
    private boolean isWhitelistTenant(String tenantId) {
        List<String> whitelistTenants = gatewayProperties.getRateLimit().getWhitelistTenants();
        if (whitelistTenants == null || whitelistTenants.isEmpty()) {
            return false;
        }
        return whitelistTenants.contains(tenantId);
    }

    /**
     * 执行限流检查
     */
    private Mono<Void> doRateLimitCheck(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String tenantId, JSONObject packageInfo) {
        JSONObject rateLimitRules = packageInfo.getJSONObject("rateLimitRules");
        if (rateLimitRules == null) {
            log.debug("No rateLimitRules found for tenant: {}, skipping rate limit", tenantId);
            return chain.filter(exchange);
        }

        Integer qpsLimit = rateLimitRules.getInt("qps");
        Long dailyLimit = rateLimitRules.getLong("dailyLimit");
        Long monthlyLimit = rateLimitRules.getLong("monthlyLimit");

        log.debug("Using rate limit rules for tenant {}: qps={}, daily={}, monthly={}",
                tenantId, qpsLimit, dailyLimit, monthlyLimit);

        // 从限制最严格的开始检查：月 -> 日 -> QPS
        return checkMonthlyLimit(tenantId, monthlyLimit)
                .flatMap(monthlyAllowed -> {
                    if (!Boolean.TRUE.equals(monthlyAllowed)) {
                        return tooManyRequestsResponse(exchange, "Monthly limit exceeded");
                    }
                    return checkDailyLimit(tenantId, dailyLimit);
                })
                .flatMap(dailyAllowed -> {
                    if (!Boolean.TRUE.equals(dailyAllowed)) {
                        return tooManyRequestsResponse(exchange, "Daily limit exceeded");
                    }
                    return checkQpsLimit(tenantId, qpsLimit);
                })
                .flatMap(qpsAllowed -> {
                    if (!Boolean.TRUE.equals(qpsAllowed)) {
                        return tooManyRequestsResponse(exchange, "QPS limit exceeded");
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * 检查并增加QPS计数
     */
    private Mono<Boolean> checkQpsLimit(String tenantId, Integer maxQps) {
        if (maxQps == null || maxQps <= 0) {
            return Mono.just(true);
        }

        String qpsKey = GatewayConstants.REDIS_KEY_PREFIX_RATE_LIMIT + "qps:" + tenantId;

        return redisTemplate.opsForValue()
                .get(qpsKey)
                .defaultIfEmpty("0")
                .map(Long::parseLong)
                .flatMap(currentCount -> {
                    if (currentCount >= maxQps) {
                        log.warn("QPS limit exceeded for tenant: {}, current: {}, max: {}", tenantId, currentCount, maxQps);
                        return Mono.just(false);
                    }
                    return redisTemplate.opsForValue()
                            .increment(qpsKey)
                            .flatMap(newCount -> {
                                if (newCount == 1) {
                                    return redisTemplate.expire(qpsKey, Duration.ofSeconds(1))
                                            .thenReturn(true);
                                }
                                return Mono.just(true);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error checking QPS limit for tenant: {}", tenantId, e);
                    return Mono.just(true);
                });
    }

    /**
     * 检查并增加日计数
     */
    private Mono<Boolean> checkDailyLimit(String tenantId, Long maxDailyLimit) {
        if (maxDailyLimit == null || maxDailyLimit <= 0) {
            return Mono.just(true);
        }

        String today = LocalDate.now().format(DATE_FORMATTER);
        String dailyKey = GatewayConstants.REDIS_KEY_PREFIX_RATE_LIMIT + "daily:" + today + ":" + tenantId;
        long expireSeconds = gatewayProperties.getRateLimit().getDailyLimitExpireSeconds();

        return redisTemplate.opsForValue()
                .get(dailyKey)
                .defaultIfEmpty("0")
                .map(Long::parseLong)
                .flatMap(currentCount -> {
                    if (currentCount >= maxDailyLimit) {
                        log.warn("Daily limit exceeded for tenant: {}, current: {}, max: {}", tenantId, currentCount, maxDailyLimit);
                        return Mono.just(false);
                    }
                    return redisTemplate.opsForValue()
                            .increment(dailyKey)
                            .flatMap(newCount -> {
                                if (newCount == 1) {
                                    return redisTemplate.expire(dailyKey, Duration.ofSeconds(expireSeconds))
                                            .thenReturn(true);
                                }
                                return Mono.just(true);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error checking daily limit for tenant: {}", tenantId, e);
                    return Mono.just(true);
                });
    }

    /**
     * 检查并增加月计数
     */
    private Mono<Boolean> checkMonthlyLimit(String tenantId, Long maxMonthlyLimit) {
        if (maxMonthlyLimit == null || maxMonthlyLimit <= 0) {
            return Mono.just(true);
        }

        String month = LocalDate.now().format(MONTH_FORMATTER);
        String monthlyKey = GatewayConstants.REDIS_KEY_PREFIX_RATE_LIMIT + "monthly:" + month + ":" + tenantId;
        long expireSeconds = gatewayProperties.getRateLimit().getMonthlyLimitExpireSeconds();

        return redisTemplate.opsForValue()
                .get(monthlyKey)
                .defaultIfEmpty("0")
                .map(Long::parseLong)
                .flatMap(currentCount -> {
                    if (currentCount >= maxMonthlyLimit) {
                        log.warn("Monthly limit exceeded for tenant: {}, current: {}, max: {}", tenantId, currentCount, maxMonthlyLimit);
                        return Mono.just(false);
                    }
                    return redisTemplate.opsForValue()
                            .increment(monthlyKey)
                            .flatMap(newCount -> {
                                if (newCount == 1) {
                                    return redisTemplate.expire(monthlyKey, Duration.ofSeconds(expireSeconds))
                                            .thenReturn(true);
                                }
                                return Mono.just(true);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error checking monthly limit for tenant: {}", tenantId, e);
                    return Mono.just(true);
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
     * 返回请求过多响应
     */
    private Mono<Void> tooManyRequestsResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (!response.isCommitted()) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            response.getHeaders().add("X-Rate-Limit-Retry-After-Seconds", "1");
            String body = "{\"code\":429,\"message\":\"" + message + "\"}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
        return Mono.empty();
    }

    /**
     * 返回租户未找到响应
     */
    private Mono<Void> tenantNotFoundResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        if (!response.isCommitted()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            String body = "{\"code\":403,\"message\":\"租户不存在或未配置套餐\"}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }
}
