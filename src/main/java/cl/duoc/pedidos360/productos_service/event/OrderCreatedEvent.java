package cl.duoc.pedidos360.productos_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderCreatedEvent {
    private Long orderId;
    private List<ItemEvent> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemEvent {
        private Long productId;
        private Integer quantity;
    }
}
