package cn.structured.cloud.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局网关过滤器
 * 
 * 职责：处理所有进入网关的请求
 * 目前作为预留过滤器，可用于全局预处理逻辑
 * 
 * 执行顺序：Ordered.HIGHEST_PRECEDENCE（最高优先级）
 */
@Component
public class GlobalGatewayFilter implements GlobalFilter, Ordered {

    private final StructureGatewayProperties gatewayProperties;

    public GlobalGatewayFilter(StructureGatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange);
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
