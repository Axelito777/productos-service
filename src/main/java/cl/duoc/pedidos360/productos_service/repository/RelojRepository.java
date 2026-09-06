package cl.duoc.pedidos360.productos_service.repository;

import cl.duoc.pedidos360.productos_service.model.Reloj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelojRepository extends JpaRepository<Reloj, Long> {

    List<Reloj> findByCategoriaIgnoreCase(String categoria);

    List<Reloj> findByMarcaIgnoreCase(String marca);

}
