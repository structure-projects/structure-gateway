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
import java.util.List;

/**
 * 租户识别过滤器
 * <p>
 * 职责：从请求头中提取租户信息（Tenant-Id）
 * 如果请求头中未提供，则使用默认值
 * 将租户信息写入请求头，供后续过滤器和下游服务使用
 * <p>
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE + 20
 */
@Component
public class TenantIdentificationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantIdentificationFilter.class);

    @Autowired
    private StructureGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getTenantIdentification().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String tenantId = request.getHeaders().getFirst(GatewayConstants.TENANT_ID_HEADER);
        StructureGatewayProperties.TenantIdentification tenantConfig = gatewayProperties.getTenantIdentification();

        if (StrUtil.isBlank(tenantId)) {
            tenantId = tenantConfig.getDefaultTenantId();
            log.debug("Tenant ID not found in header, using default: {}", tenantId);
        } else if (tenantId.length() > tenantConfig.getMaxTenantIdLength()) {
            log.warn("Invalid tenant ID length: {} for request: {}", tenantId.length(), path);
            return badRequestResponse(exchange, "X-Tenant-Id must not exceed " + tenantConfig.getMaxTenantIdLength() + " characters");
        }

        final String finalTenantId = tenantId;

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(GatewayConstants.TENANT_ID_HEADER, finalTenantId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        log.debug("Tenant identified - ID: {} for path: {}", finalTenantId, path);

        return chain.filter(mutatedExchange);
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
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
