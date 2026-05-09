package cn.structured.cloud.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由定位器配置
 * 
 * 职责：配置网关路由规则
 * 支持动态路由配置和服务发现
 */
@Configuration
@EnableConfigurationProperties(StructureGatewayProperties.class)
public class RouteLocatorConfig {

    private final StructureGatewayProperties gatewayProperties;

    public RouteLocatorConfig(StructureGatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    /**
     * 自定义路由配置
     * 可以在这里添加静态路由规则
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .build();
    }
}
