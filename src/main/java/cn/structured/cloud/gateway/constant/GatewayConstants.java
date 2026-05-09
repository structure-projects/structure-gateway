package cn.structured.cloud.gateway.constant;

/**
 * 网关常量定义
 */
public final class GatewayConstants {

    /** Token请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Bearer Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Bearer标识 */
    public static final String TOKEN_BEARER_PREFIX = "Bearer";

    /** 租户ID请求头名称 */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    /** 请求ID请求头名称 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 时间戳请求头名称 */
    public static final String REQUEST_TIMESTAMP_HEADER = "X-Timestamp";

    /** 签名请求头名称 */
    public static final String REQUEST_SIGNATURE_HEADER = "X-Signature";

    /** 随机字符串请求头名称 */
    public static final String REQUEST_NONCE_HEADER = "X-Nonce";

    /** Redis限流Key前缀 */
    public static final String REDIS_KEY_PREFIX_RATE_LIMIT = "gateway:rate:limit:";

    /** Redis重放防护Key前缀 */
    public static final String REDIS_KEY_PREFIX_REPLAY = "gateway:replay:";

    /** Redis租户套餐Key前缀 */
    public static final String REDIS_KEY_PREFIX_TENANT_PACKAGE = "gateway:tenant:package:";

    /** RabbitMQ 租户套餐交换器名称 */
    public static final String TENANT_PACKAGE_EXCHANGE = "gateway.tenant.package.exchange";

    /** RabbitMQ 租户套餐队列名称 */
    public static final String TENANT_PACKAGE_QUEUE = "gateway.tenant.package.queue";

    /** RabbitMQ 租户套餐路由键 */
    public static final String TENANT_PACKAGE_ROUTING_KEY = "gateway.tenant.package";

    private GatewayConstants() {
        // 私有构造函数，防止实例化
    }
}
