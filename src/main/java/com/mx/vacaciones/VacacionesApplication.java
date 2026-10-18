package com.mx.vacaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de INSSOFT PEOPLE.
 *
 * <p>Durante la transición conviven dos paquetes:</p>
 * <ul>
 *   <li>{@code com.mx.vacaciones}: sistema legado (Thymeleaf).</li>
 *   <li>{@code com.inssoft.people}: nuevos módulos (API v1).</li>
 * </ul>
 *
 * <p>El escaneo explícito es necesario porque la clase principal vive en el
 * paquete legado y los nuevos módulos están en otra raíz.</p>
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.mx.vacaciones", "com.inssoft.people"})
@EnableJpaRepositories(basePackages = {"com.mx.vacaciones.repository", "com.inssoft.people"})
@EntityScan(basePackages = {"com.mx.vacaciones.model", "com.inssoft.people"})
public class VacacionesApplication {

    private static final Logger log = LoggerFactory.getLogger(VacacionesApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(VacacionesApplication.class, args);
        log.info("INSSOFT PEOPLE iniciado correctamente");
    }
}
