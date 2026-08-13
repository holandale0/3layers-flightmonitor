package com.flightmonitor.core.search.boundary.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do worker.
 *
 * <p>Os dois timeouts sao bem diferentes de proposito. A varredura consulta uma
 * API rapida e cacheada; a confirmacao vai ao Google ao vivo e pode levar
 * segundos. Um timeout unico ou seria curto demais para a camada 2, ou longo
 * demais para detectar a camada 1 travada.
 */
@ConfigurationProperties(prefix = "flightmonitor.worker")
public record WorkerProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration scanTimeout,
        Duration confirmTimeout,
        Transporte transporte) {

    /**
     * Como o core fala com o worker — etapa E4.1.
     *
     * <p>A escolha e de <b>transporte</b>, e nao de comportamento: os dois
     * adaptadores implementam o mesmo contrato, com a mesma semantica sincrona e
     * o mesmo tratamento de erro. Trocar nao muda o que o sistema faz.
     */
    public enum Transporte { REST, AMQP }

    public WorkerProperties {
        baseUrl = baseUrl == null ? "http://localhost:8001" : baseUrl;
        transporte = transporte == null ? Transporte.REST : transporte;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        scanTimeout = scanTimeout == null ? Duration.ofSeconds(30) : scanTimeout;
        confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(60) : confirmTimeout;
    }
}
