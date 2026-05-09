
package cn.structured.cloud.gateway.config;

import cn.structured.cloud.gateway.constant.GatewayConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 租户套餐队列
     */
    @Bean
    public Queue tenantPackageQueue() {
        return new Queue(GatewayConstants.TENANT_PACKAGE_QUEUE, true, false, false);
    }

    /**
     * 租户套餐交换器
     */
    @Bean
    public DirectExchange tenantPackageExchange() {
        return new DirectExchange(GatewayConstants.TENANT_PACKAGE_EXCHANGE, true, false);
    }

    /**
     * 绑定租户套餐队列和交换器
     */
    @Bean
    public Binding tenantPackageBinding() {
        return BindingBuilder
                .bind(tenantPackageQueue())
                .to(tenantPackageExchange())
                .with(GatewayConstants.TENANT_PACKAGE_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
