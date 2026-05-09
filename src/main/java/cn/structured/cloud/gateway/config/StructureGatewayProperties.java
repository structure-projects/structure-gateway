package cn.structured.cloud.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置属性类
 * 支持通过 Nacos 动态刷新配置
 * 配置前缀: structure.gateway
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "structure.gateway")
public class StructureGatewayProperties {

    /** Token验证配置 */
    private TokenCheck tokenCheck = new TokenCheck();

    /** 租户识别配置 */
    private TenantIdentification tenantIdentification = new TenantIdentification();

    /** 限流配置 */
    private RateLimit rateLimit = new RateLimit();

    /** 重放攻击防护配置 */
    private ReplayCheck replayCheck = new ReplayCheck();

    /** 链路追踪配置 */
    private TraceConfig traceConfig = new TraceConfig();

    /** 排除路径（跳过部分检查） */
    private List<String> excludedPaths = new ArrayList<>();

    /**
     * Token验证配置
     */
    @Data
    public static class TokenCheck {
        /** 是否启用Token验证 */
        private boolean enabled = true;

        /** Token最小长度 */
        private int minTokenLength = 20;
    }

    /**
     * 租户识别配置
     */
    @Data
    public static class TenantIdentification {
        /** 是否启用租户识别 */
        private boolean enabled = true;

        /** 默认租户ID */
        private String defaultTenantId = "default";

        /** 租户ID最大长度 */
        private int maxTenantIdLength = 64;
    }

    /**
     * 限流配置
     */
    @Data
    public static class RateLimit {
        /** 是否启用限流 */
        private boolean enabled = true;

        /** 日限流 Key 过期时间（秒），默认1天 */
        private long dailyLimitExpireSeconds = 86400L;

        /** 月限流 Key 过期时间（秒），默认31天 */
        private long monthlyLimitExpireSeconds = 2678400L;

        /** 白名单租户列表，白名单租户不进行限流验证 */
        private List<String> whitelistTenants = new ArrayList<>();
    }

    /**
     * 套餐QPS配置
     */
    @Data
    public static class PackageQpsConfig {
        /** QPS限制 */
        private int qps = 100;

        /** 日限流数量，0表示不限流 */
        private long dailyLimit = 0;

        /** 月限流数量，0表示不限流 */
        private long monthlyLimit = 0;
    }

    /**
     * 重放攻击防护配置
     */
    @Data
    public static class ReplayCheck {
        /** 是否启用重放攻击防护 */
        private boolean enabled = true;

        /** 时间戳容差（毫秒），默认5分钟 */
        private long timestampToleranceMs = 300000L;

        /** 是否要求X-Timestamp */
        private boolean requireTimestamp = true;

        /** 是否要求X-Signature */
        private boolean requireSignature = false;

        /** 是否要求X-Nonce */
        private boolean requireNonce = false;

        /** Nonce最小长度 */
        private int minNonceLength = 8;

        /** Nonce最大长度 */
        private int maxNonceLength = 64;

        /** Signature最小长度 */
        private int minSignatureLength = 32;

        /** Nonce在Redis中的过期时间（分钟） */
        private long nonceExpireMinutes = 10;
    }

    /**
     * 链路追踪配置
     */
    @Data
    public static class TraceConfig {
        /** 是否启用链路追踪 */
        private boolean enabled = true;

        /** 是否要求X-Request-Id */
        private boolean requireRequestId = true;
    }
}
