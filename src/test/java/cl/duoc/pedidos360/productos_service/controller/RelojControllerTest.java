package cl.duoc.pedidos360.productos_service.controller;

import cl.duoc.pedidos360.productos_service.config.KafkaTopics;
import cl.duoc.pedidos360.productos_service.event.UserEventDto;
import cl.duoc.pedidos360.productos_service.event.UserEventPublisher;
import cl.duoc.pedidos360.productos_service.event.UserEventType;
import cl.duoc.pedidos360.productos_service.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.service.RelojService;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** GET /productos/{id}: publica PRODUCT_VIEWED solo con token; nunca falla por Kafka */
@ExtendWith(MockitoExtension.class)
class RelojControllerTest {

    @Mock
    private RelojService relojService;

    @Mock
    private KafkaTemplate<String, UserEventDto> kafkaTemplate;

    private RelojController controller;

    private final Reloj casio = new Reloj(1L, "Casio", "G-Shock", "desc", new BigDecimal("99990"), 10, "Deportivo", null);

    private static String token(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer eyJhbGciOiJub25lIn0." + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".firma";
    }

    @BeforeEach
    void setUp() {
        controller = new RelojController(relojService, new UserEventPublisher(kafkaTemplate));
    }

    @Test
    void conToken_publicaProductViewedConCategoriaYMarca() {
        when(relojService.obtenerPorId(1L)).thenReturn(casio);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new java.util.concurrent.CompletableFuture<>());

        ResponseEntity<Reloj> r = controller.obtenerPorId(1L, token("{\"oid\":\"oid-ana\"}"));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).isEqualTo(casio);
        ArgumentCaptor<UserEventDto> evento = ArgumentCaptor.forClass(UserEventDto.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.USER_EVENTS), eq("oid-ana"), evento.capture());
        assertThat(evento.getValue().eventType()).isEqualTo(UserEventType.PRODUCT_VIEWED);
        assertThat(evento.getValue().userId()).isEqualTo("oid-ana");
        assertThat(evento.getValue().productId()).isEqualTo(1L);
        assertThat(evento.getValue().productCategory()).isEqualTo("Deportivo");
        assertThat(evento.getValue().brand()).isEqualTo("Casio");
        assertThat(evento.getValue().quantity()).isNull();
    }

    @Test
    void sinToken_noPublica() {
        when(relojService.obtenerPorId(1L)).thenReturn(casio);

        ResponseEntity<Reloj> r = controller.obtenerPorId(1L, null);

        assertThat(r.getBody()).isEqualTo(casio);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void kafkaCaido_devuelveElRelojIgual() {
        when(relojService.obtenerPorId(1L)).thenReturn(casio);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new KafkaException("broker caído"));

        ResponseEntity<Reloj> r = controller.obtenerPorId(1L, token("{\"oid\":\"oid-ana\"}"));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).isEqualTo(casio);
    }

    @Test
    void relojInexistente_noPublica() {
        when(relojService.obtenerPorId(99L)).thenThrow(new ResourceNotFoundException("No existe un reloj con id 99"));

        assertThatThrownBy(() -> controller.obtenerPorId(99L, token("{\"oid\":\"oid-ana\"}")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(kafkaTemplate);
    }
}
