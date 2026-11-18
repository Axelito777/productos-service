package cl.duoc.pedidos360.productos_service.event;

import cl.duoc.pedidos360.productos_service.config.KafkaTopics;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private KafkaTemplate<String, UserEventDto> kafkaTemplate;

    @Test
    void publicaEnUserEventsConUserIdComoKey() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        new UserEventPublisher(kafkaTemplate).publicar(UserEventType.PRODUCT_VIEWED, "oid-1", 5L, "Deportivo", "Casio", null);

        ArgumentCaptor<UserEventDto> evento = ArgumentCaptor.forClass(UserEventDto.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.USER_EVENTS), eq("oid-1"), evento.capture());
        assertThat(evento.getValue().eventId()).isNotBlank();
        assertThat(evento.getValue().productCategory()).isEqualTo("Deportivo");
        assertThat(evento.getValue().brand()).isEqualTo("Casio");
        assertThat(evento.getValue().timestamp()).isNotNull();
    }

    @Test
    void sinUserId_noPublica() {
        new UserEventPublisher(kafkaTemplate).publicar(UserEventType.PRODUCT_VIEWED, null, 5L, null, null, null);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void kafkaCaido_noLanzaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new KafkaException("broker caído"));

        assertThatCode(() -> new UserEventPublisher(kafkaTemplate)
                .publicar(UserEventType.PRODUCT_VIEWED, "oid-1", 5L, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void errorAsincrono_noLanzaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("timeout")));

        assertThatCode(() -> new UserEventPublisher(kafkaTemplate)
                .publicar(UserEventType.PRODUCT_VIEWED, "oid-1", 5L, null, null, null))
                .doesNotThrowAnyException();
    }
}
