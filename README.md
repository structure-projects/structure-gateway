# Structure Gateway

基于 Spring Cloud Gateway 的多租户 API 网关服务，提供统一的 API 入口、安全认证、租户隔离和限流等功能。

## 功能特性

### 核心功能

- **路由转发**：基于路径的请求路由到不同的微服务
- **服务发现**：集成 Nacos 服务注册与发现
- **配置中心**：支持 Nacos 配置中心动态刷新
- **CORS 跨域**：全局跨域支持

### 安全与认证

- **Token 验证**：验证 Bearer Token 格式和有效性
- **租户识别**：自动识别和处理租户信息
- **重放攻击防护**：时间戳 + Nonce 机制防止重放攻击

### 限流控制

- **QPS 限流**：按每秒请求数限制
- **日限流**：按每日请求总数限制
- **月限流**：按每月请求总数限制
- **白名单支持**：白名单租户不限流
- **动态限流配置**：通过 Redis 存储和读取租户套餐配置

### 链路追踪

- **请求 ID 生成和传递**：自动生成或传递 X-Request-Id

## 技术栈

- **Java 8**
- **Spring Boot 2.7.18**
- **Spring Cloud Gateway**
- **Spring Cloud Alibaba (Nacos)**
- **Spring Data Redis (Reactive)**
- **RabbitMQ**
- **Hutool 工具库**
- **Lombok**

## 项目结构

```
structure-gateway/
├── src/main/java/cn/structured/cloud/gateway/
│   ├── config/              # 配置类
│   │   ├── GlobalGatewayFilter.java
│   │   ├── RabbitMQConfig.java
│   │   ├── RedisConfig.java
│   │   ├── RouteLocatorConfig.java
│   │   └── StructureGatewayProperties.java
│   ├── constant/            # 常量定义
│   │   └── GatewayConstants.java
│   ├── dto/                 # 数据传输对象
│   │   └── TenantPackageMessage.java
│   ├── filter/              # 网关过滤器
│   │   ├── ReplayAttackPreventionFilter.java  # 重放攻击防护
│   │   ├── TenantIdentificationFilter.java    # 租户识别
│   │   ├── TenantRateLimitFilter.java         # 租户限流
│   │   ├── TokenVerificationFilter.java       # Token验证
│   │   └── TraceHeaderFilter.java             # 链路追踪
│   ├── listener/            # 消息监听器
│   │   └── TenantPackageMessageListener.java
│   └── GatewayApplication.java               # 启动类
├── src/main/resources/
│   ├── application.yaml     # 应用配置
│   └── bootstrap.yaml       # 引导配置
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- Redis 5.0+
- RabbitMQ 3.8+ (可选)
- Nacos 2.0+ (可选)

### 本地运行

1. **克隆项目**

```bash
git clone <repository-url>
cd structure-gateway
```

2. **配置环境变量**（可选，使用默认值可跳过）

```bash
# Nacos 配置
export NACOS_SERVER=nacos.structured.cn:8848
export NACOS_NAMESPACE=5a4e4c1f-beda-4ae5-a3d7-428950e7473b
export NACOS_GROUP=dev
export NACOS_DISCOVERY_ENABLE=false
export NACOS_CONFIG_ENABLE=false
export NACOS_USERNAME=structure
export NACOS_PASSWORD=structure

# Redis 配置
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=123456
export REDIS_DATABASE=0

# RabbitMQ 配置
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=root
export RABBITMQ_PASSWORD=123456
export RABBITMQ_VHOST=/
```

3. **启动服务**

```bash
mvn clean package
java -jar target/gateway.jar
```

或使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

4. **验证服务**

服务默认运行在 `http://localhost:18000`

```bash
curl http://localhost:18000/actuator/health
```

## 配置说明

### 网关配置 (structure.gateway)

```yaml
structure:
  gateway:
    # Token 验证配置
    token-check:
      enabled: true                    # 是否启用
      min-token-length: 20             # Token 最小长度
    # 租户识别配置
    tenant-identification:
      enabled: true                    # 是否启用
      default-tenant-id: default       # 默认租户 ID
      max-tenant-id-length: 64         # 租户 ID 最大长度
    # 限流配置
    rate-limit:
      enabled: true                    # 是否启用
      whitelist-tenants:               # 白名单租户
        - "1"
        - "2"
      daily-limit-expire-seconds: 86400    # 日限流 Key 过期时间（秒）
      monthly-limit-expire-seconds: 2678400 # 月限流 Key 过期时间（秒）
    # 重放攻击防护配置
    replay-check:
      enabled: true                    # 是否启用
      timestamp-tolerance-ms: 300000   # 时间戳容差（毫秒，默认5分钟）
      require-timestamp: true          # 是否要求时间戳
      require-signature: false         # 是否要求签名
      require-nonce: false             # 是否要求 Nonce
      min-nonce-length: 8              # Nonce 最小长度
      max-nonce-length: 64             # Nonce 最大长度
      min-signature-length: 32         # 签名最小长度
      nonce-expire-minutes: 10         # Nonce 过期时间（分钟）
    # 链路追踪配置
    trace-config:
      enabled: true                    # 是否启用
      require-request-id: true         # 是否要求请求 ID
    # 排除路径（跳过所有检查）
    excluded-paths:
      - /auth/**
      - /actuator/**
```

### 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-center
          uri: http://localhost:18002
          predicates:
            - Path=/auth/**
        - id: user-center
          uri: http://localhost:18001
          predicates:
            - Path=/user/**
```

## 请求头说明

| Header 名称 | 说明 | 是否必填 | 示例 |
|------------|------|---------|------|
| Authorization | Bearer Token | 是（非排除路径） | `Bearer eyJhbGciOiJIUzI1NiIs...` |
| X-Tenant-Id | 租户 ID | 否（有默认值） | `tenant-123` |
| X-Request-Id | 请求追踪 ID | 否（自动生成） | `uuid-xxxx-xxxx` |
| X-Timestamp | 请求时间戳（毫秒） | 是（重放检查启用时） | `1715345678000` |
| X-Nonce | 随机字符串 | 是（重放检查启用且 require-nonce=true） | `abc123xyz` |
| X-Signature | 请求签名 | 是（重放检查启用且 require-signature=true） | `sha256hash` |

## 限流配置

### Redis 中的套餐数据结构

Key: `gateway:tenant:package:{tenantId}`

Value (JSON 格式):

```json
{
  "rateLimitEnabled": true,
  "rateLimitRules": {
    "qps": 100,
    "dailyLimit": 10000,
    "monthlyLimit": 300000
  }
}
```

### 限流 Key 格式

- QPS 限流: `gateway:rate:limit:qps:{tenantId}` (过期时间: 1秒)
- 日限流: `gateway:rate:limit:daily:{yyyyMMdd}:{tenantId}` (过期时间: 1天)
- 月限流: `gateway:rate:limit:monthly:{yyyyMM}:{tenantId}` (过期时间: 31天)

### 套餐变更消息

通过 RabbitMQ 接收租户套餐变更消息，消息格式:

```json
{
  "tenantId": "1",
  "operation": "UPDATE",
  "packageData": {
    "rateLimitEnabled": true,
    "rateLimitRules": {
      "qps": 200,
      "dailyLimit": 20000,
      "monthlyLimit": 600000
    }
  }
}
```

## 开发指南

### 添加新的过滤器

1. 创建过滤器类，实现 `GlobalFilter` 和 `Ordered` 接口

```java
@Component
public class MyCustomFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 前置处理
        ServerHttpRequest request = exchange.getRequest();
        // 自定义逻辑
        
        // 继续过滤器链
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 设置执行顺序
    }
}
```

2. 在 `StructureGatewayProperties` 中添加配置（如需要）

3. 在 `application.yaml` 中配置默认值

### 本地调试

1. **禁用 Nacos**（使用本地配置）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
```

2. **配置本地路由**

在 `application.yaml` 中配置指向本地服务的路由

3. **启动依赖服务**

确保 Redis 正在运行

4. **启动网关**

使用 IDE 或 Maven 启动

### 测试

使用 curl 或 Postman 测试网关功能：

```bash
# 测试排除路径（无需认证）
curl http://localhost:18000/auth/login

# 测试需要认证的路径
curl -H "Authorization: Bearer your-token-here" \
     -H "X-Tenant-Id: 1" \
     http://localhost:18000/user/profile
```

## 监控

### Actuator 端点

- `/actuator/health` - 健康检查
- `/actuator/info` - 服务信息
- `/actuator/gateway` - 网关信息

### 日志级别

```yaml
logging:
  level:
    cn.structured.cloud.gateway: DEBUG
    org.springframework.cloud.gateway: INFO
```

## 部署

### Docker 部署

```dockerfile
FROM openjdk:8-jre-alpine
WORKDIR /app
COPY target/gateway.jar app.jar
EXPOSE 18000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes 部署

参考 `.github/workflows/build-gateway-and-push.yml` 中的 CI/CD 流程

## 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件
