package cl.duoc.pedidos360.productos_service.listener;

import cl.duoc.pedidos360.productos_service.config.RabbitConfig;
import cl.duoc.pedidos360.productos_service.event.OrderCreatedEvent;
import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.repository.RelojRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventListener {

    private static final int MAX_REINTENTOS = 3;

    private final RelojRepository relojRepository;

    @RabbitListener(queues = RabbitConfig.STOCK_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void descontarStock(OrderCreatedEvent event, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        boolean procesado = false;
        Exception ultimoError = null;

        for (int intento = 1; intento <= MAX_REINTENTOS && !procesado; intento++) {
            try {
                log.info("Procesando orden {} (intento {}/{})", event.getOrderId(), intento, MAX_REINTENTOS);

                for (OrderCreatedEvent.ItemEvent item : event.getItems()) {
                    Reloj reloj = relojRepository.findById(item.getProductId())
                            .orElseThrow(() -> new RuntimeException("Producto " + item.getProductId() + " no encontrado"));

                    reloj.setStock(reloj.getStock() - item.getQuantity());
                    relojRepository.save(reloj);
                    log.info("Stock descontado: producto {} -{}", item.getProductId(), item.getQuantity());
                }
                procesado = true;

            } catch (Exception e) {
                ultimoError = e;
                log.warn("Fallo intento {}/{} para orden {}: {}",
                        intento, MAX_REINTENTOS, event.getOrderId(), e.getMessage());
            }
        }

        try {
            if (procesado) {
                channel.basicAck(tag, false);
            } else {
                log.error("Orden {} falló tras {} intentos, enviando a DLQ. Último error: {}",
                        event.getOrderId(), MAX_REINTENTOS,
                        ultimoError != null ? ultimoError.getMessage() : "desconocido");
                channel.basicNack(tag, false, false); // false, false = no requeue -> va directo a la DLQ
            }
        } catch (IOException io) {
            log.error("Error confirmando ack/nack en RabbitMQ: {}", io.getMessage());
        }
    }
}
