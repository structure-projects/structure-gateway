
package cn.structured.cloud.gateway.listener;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.structured.cloud.gateway.constant.GatewayConstants;
import cn.structured.cloud.gateway.dto.TenantPackageMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 租户套餐消息监听器
 * 监听 RabbitMQ 消息，更新 Redis 中的租户套餐信息
 */
@Slf4j
@Component
public class TenantPackageMessageListener {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 监听租户套餐消息
     */
    @RabbitListener(queues = GatewayConstants.TENANT_PACKAGE_QUEUE)
    public void handleTenantPackageMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("收到租户套餐消息: {}", body);

            TenantPackageMessage tenantPackageMessage = parseMessage(body);
            if (tenantPackageMessage == null) {
                log.warn("消息格式错误，拒绝消息: {}", body);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            String tenantId = tenantPackageMessage.getTenantId();
            String operation = tenantPackageMessage.getOperation();

            if (tenantId == null || tenantId.trim().isEmpty()) {
                log.warn("租户ID为空，拒绝消息: {}", body);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            log.info("处理租户套餐变更, 租户ID: {}, 操作类型: {}", tenantId, operation);

            switch (operation.toUpperCase()) {
                case "CREATE":
                case "UPDATE":
                    saveOrUpdateTenantPackage(tenantId, tenantPackageMessage);
                    break;
                case "DELETE":
                    deleteTenantPackage(tenantId);
                    break;
                default:
                    log.warn("未知的操作类型: {}, 拒绝消息", operation);
                    channel.basicNack(deliveryTag, false, false);
                    return;
            }

            channel.basicAck(deliveryTag, false);
            log.info("租户套餐变更处理成功: {}", tenantId);

        } catch (Exception e) {
            log.error("处理租户套餐消息失败", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 解析消息
     */
    private TenantPackageMessage parseMessage(String body) {
        try {
            return JSONUtil.toBean(body, TenantPackageMessage.class);
        } catch (Exception e) {
            log.error("解析消息失败", e);
            return null;
        }
    }

    /**
     * 保存或更新租户套餐到 Redis
     */
    private void saveOrUpdateTenantPackage(String tenantId, TenantPackageMessage message) {
        String key = GatewayConstants.REDIS_KEY_PREFIX_TENANT_PACKAGE + tenantId;

        JSONObject packageData = new JSONObject();
        packageData.set("packageType", message.getPackageType());
        packageData.set("rateLimitEnabled", message.getRateLimitEnabled() != null ? message.getRateLimitEnabled() : true);

        if (message.getRateLimitRules() != null) {
            JSONObject rules = new JSONObject();
            rules.set("qps", message.getRateLimitRules().getQps());
            rules.set("dailyLimit", message.getRateLimitRules().getDailyLimit());
            rules.set("monthlyLimit", message.getRateLimitRules().getMonthlyLimit());
            packageData.set("rateLimitRules", rules);
        }

        stringRedisTemplate.opsForValue().set(key, packageData.toString());
        log.info("租户套餐已更新到 Redis: {}", tenantId);
    }

    /**
     * 删除租户套餐
     */
    private void deleteTenantPackage(String tenantId) {
        String key = GatewayConstants.REDIS_KEY_PREFIX_TENANT_PACKAGE + tenantId;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.info("删除租户套餐: {}, 结果: {}", tenantId, deleted);
    }
}
