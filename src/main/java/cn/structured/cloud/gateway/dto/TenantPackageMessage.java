
package cn.structured.cloud.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 租户套餐消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPackageMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 操作类型: CREATE/UPDATE/DELETE
     */
    private String operation;

    /**
     * 套餐类型
     */
    private String packageType;

    /**
     * 是否启用限流
     */
    private Boolean rateLimitEnabled;

    /**
     * 限流规则
     */
    private RateLimitRules rateLimitRules;

    /**
     * 限流规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitRules implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * QPS 限制
         */
        private Integer qps;

        /**
         * 每日限制
         */
        private Long dailyLimit;

        /**
         * 每月限制
         */
        private Long monthlyLimit;
    }
}
