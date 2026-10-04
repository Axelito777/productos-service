package cl.duoc.pedidos360.productos_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDERS_DIRECT_EXCHANGE = "orders.direct";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String STOCK_QUEUE = "stock-queue";
    public static final String STOCK_QUEUE_DLQ = "stock-queue.dlq";
    public static final String DLX_EXCHANGE = "dlx.exchange";

    @Bean
    public DirectExchange ordersDirectExchange() {
        return new DirectExchange(ORDERS_DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockQueue() {
        return QueueBuilder.durable(STOCK_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", STOCK_QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue stockQueueDlq() {
        return QueueBuilder.durable(STOCK_QUEUE_DLQ).build();
    }

    @Bean
    public Binding stockQueueBinding(Queue stockQueue, DirectExchange ordersDirectExchange) {
        return BindingBuilder.bind(stockQueue).to(ordersDirectExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding stockQueueDlqBinding(Queue stockQueueDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(stockQueueDlq).to(dlxExchange).with(STOCK_QUEUE_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // clave: ACK/NACK a mano, no automático
        return factory;
    }
}
