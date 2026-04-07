package com.mx.vacaciones;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VacacionesApplication {

    private static final String LOG_DIR = "C:/logs";

    
	public static void main(String[] args) {
		File dir = new File(LOG_DIR);

        if (!dir.exists()) {
            boolean created = dir.mkdirs();

            if (created) {
                System.out.println("Carpeta de logs creada: " + LOG_DIR);
            } else {
                System.out.println("No se pudo crear la carpeta de logs: " + LOG_DIR);
            }
        }
		SpringApplication.run(VacacionesApplication.class, args);
	}
}
