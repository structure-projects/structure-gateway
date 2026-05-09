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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 链路追踪过滤器
 * <p>
 * 职责：验证和处理链路追踪相关的请求头
 * <p>
 * 验证项（可配置）：
 * - X-Request-Id：请求唯一标识（UUID格式）
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 50
 */
@Component
public class TraceHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceHeaderFilter.class);

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getTraceConfig().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        StructureGatewayProperties.TraceConfig traceConfig = gatewayProperties.getTraceConfig();
        List<String> validationErrors = new ArrayList<>();

        if (traceConfig.isRequireRequestId()) {
            String requestId = request.getHeaders().getFirst(GatewayConstants.REQUEST_ID_HEADER);
            if (StrUtil.isBlank(requestId)) {
                validationErrors.add("X-Request-Id is required");
            } else if (!isValidUUID(requestId)) {
                validationErrors.add("X-Request-Id must be a valid UUID");
            }
        }

        if (!validationErrors.isEmpty()) {
            log.warn("Trace header validation failed for request: {}, errors: {}", path, validationErrors);
            return badRequestResponse(exchange, String.join("; ", validationErrors));
        }

        return chain.filter(exchange);
    }

    /**
     * 验证UUID格式是否正确
     */
    private boolean isValidUUID(String str) {
        if (str == null || str.length() != 36) {
            return false;
        }
        String[] parts = str.split("-");
        if (parts.length != 5) {
            return false;
        }
        int[] expectedLengths = {8, 4, 4, 4, 12};
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() != expectedLengths[i]) {
                return false;
            }
            for (char c : part.toCharArray()) {
                if (!Character.isDigit(c) && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                    return false;
                }
            }
        }
        return true;
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
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
