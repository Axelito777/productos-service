package cl.duoc.pedidos360.productos_service.service;

import cl.duoc.pedidos360.productos_service.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.repository.RelojRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RelojService {

    private final RelojRepository relojRepository;

    public RelojService(RelojRepository relojRepository) {
        this.relojRepository = relojRepository;
    }

    public List<Reloj> obtenerTodos() {
        return relojRepository.findAll();
    }

    public Reloj obtenerPorId(Long id) {
        return relojRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un reloj con id " + id));
    }

    public List<Reloj> obtenerPorCategoria(String categoria) {
        return relojRepository.findByCategoriaIgnoreCase(categoria);
    }

    public Reloj crear(Reloj reloj) {
        reloj.setId(null);
        return relojRepository.save(reloj);
    }

    public Reloj actualizar(Long id, Reloj datosActualizados) {
        Reloj reloj = obtenerPorId(id);
        reloj.setMarca(datosActualizados.getMarca());
        reloj.setModelo(datosActualizados.getModelo());
        reloj.setDescripcion(datosActualizados.getDescripcion());
        reloj.setPrecio(datosActualizados.getPrecio());
        reloj.setStock(datosActualizados.getStock());
        reloj.setCategoria(datosActualizados.getCategoria());
        reloj.setImagenUrl(datosActualizados.getImagenUrl());
        return relojRepository.save(reloj);
    }

    public void eliminar(Long id) {
        Reloj reloj = obtenerPorId(id);
        relojRepository.delete(reloj);
    }

}