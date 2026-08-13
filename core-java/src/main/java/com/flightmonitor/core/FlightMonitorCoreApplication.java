package com.flightmonitor.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do core.
 *
 * <p>{@code @ConfigurationPropertiesScan} registra todos os
 * {@code @ConfigurationProperties} do pacote de uma vez, evitando espalhar
 * {@code @EnableConfigurationProperties} por varias classes de configuracao —
 * e evitando o esquecimento silencioso quando uma nova for criada.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FlightMonitorCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightMonitorCoreApplication.class, args);
	}

}
