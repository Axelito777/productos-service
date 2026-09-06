package cl.duoc.pedidos360.productos_service.config;

import cl.duoc.pedidos360.productos_service.model.Reloj;
import cl.duoc.pedidos360.productos_service.repository.RelojRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner cargarDatosIniciales(RelojRepository relojRepository) {
        return args -> {
            relojRepository.save(new Reloj(null, "Casio", "G-Shock GA-2100",
                    "Resistente a golpes, ideal para uso diario",
                    new BigDecimal("79990"), 25, "Deportivo",
                    "https://example.com/img/gshock.jpg"));

            relojRepository.save(new Reloj(null, "Seiko", "5 Sports SRPD",
                    "Automatico, correa de acero inoxidable",
                    new BigDecimal("189990"), 12, "Elegante",
                    "https://example.com/img/seiko5.jpg"));

            relojRepository.save(new Reloj(null, "Apple", "Watch Series 9",
                    "Smartwatch con GPS y monitor cardiaco",
                    new BigDecimal("349990"), 8, "Smartwatch",
                    "https://example.com/img/applewatch.jpg"));

            relojRepository.save(new Reloj(null, "Citizen", "Eco-Drive AW1231",
                    "Carga solar, resistente al agua",
                    new BigDecimal("129990"), 15, "Elegante",
                    "https://example.com/img/citizen.jpg"));
        };
    }

}
