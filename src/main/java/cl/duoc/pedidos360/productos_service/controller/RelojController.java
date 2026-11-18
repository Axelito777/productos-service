package cl.duoc.pedidos360.productos_service.controller;

import cl.duoc.pedidos360.productos_service.event.UserEventPublisher;
import cl.duoc.pedidos360.productos_service.event.UserEventType;
import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.service.RelojService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/productos")
public class RelojController {

    private final RelojService relojService;
    private final UserEventPublisher userEventPublisher;

    public RelojController(RelojService relojService, UserEventPublisher userEventPublisher) {
        this.relojService = relojService;
        this.userEventPublisher = userEventPublisher;
    }

    @GetMapping
    public ResponseEntity<List<Reloj>> listarTodos(
            @RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return ResponseEntity.ok(relojService.obtenerPorCategoria(categoria));
        }
        return ResponseEntity.ok(relojService.obtenerTodos());
    }

    /**
     * Devuelve el reloj y, si hay token, publica PRODUCT_VIEWED en Kafka (para recomendaciones).
     * Sin token (usuario anónimo) no se publica. Si Kafka falla, el reloj se devuelve igual.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Reloj> obtenerPorId(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Reloj reloj = relojService.obtenerPorId(id); // si no existe -> 404 y no se publica nada
        String userId = extraerClaim(authHeader, "oid");
        if (userId != null) {
            userEventPublisher.publicar(UserEventType.PRODUCT_VIEWED, userId, reloj.getId(),
                    reloj.getCategoria(), reloj.getMarca(), null);
        }
        return ResponseEntity.ok(reloj);
    }

    @PostMapping
    public ResponseEntity<Reloj> crear(@Valid @RequestBody Reloj reloj) {
        Reloj creado = relojService.crear(reloj);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reloj> actualizar(@PathVariable Long id, @Valid @RequestBody Reloj reloj) {
        return ResponseEntity.ok(relojService.actualizar(id, reloj));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        relojService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Lee un claim del JWT parseando el payload a mano (el API Gateway ya validó la firma) */
    private String extraerClaim(String authHeader, String claim) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            String[] partes = token.split("\\.");
            String payload = partes[1];

            int resto = payload.length() % 4;
            if (resto == 2) payload += "==";
            else if (resto == 3) payload += "=";

            String payloadJson = new String(Base64.getUrlDecoder().decode(payload));

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + claim + "\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(payloadJson);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            System.err.println("No se pudo extraer " + claim + " del token: " + e.getMessage());
            return null;
        }
    }

}