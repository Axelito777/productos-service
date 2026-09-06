package cl.duoc.pedidos360.productos_service.controller;

import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.service.RelojService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos")
public class RelojController {

    private final RelojService relojService;

    public RelojController(RelojService relojService) {
        this.relojService = relojService;
    }

    @GetMapping
    public ResponseEntity<List<Reloj>> listarTodos(
            @RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return ResponseEntity.ok(relojService.obtenerPorCategoria(categoria));
        }
        return ResponseEntity.ok(relojService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reloj> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(relojService.obtenerPorId(id));
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

}