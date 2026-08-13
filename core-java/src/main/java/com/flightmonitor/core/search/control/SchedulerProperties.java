package com.flightmonitor.core.search.control;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes do agendamento de varreduras.
 *
 * @param enabled      chave de desligamento. Permite subir a API sem o motor —
 *        util para depurar sem gastar cota das fontes, e desligado nos testes
 * @param pollInterval de quanto em quanto tempo o scheduler procura monitores
 *        vencidos. Nao e o intervalo entre varreduras de um monitor: esse fica
 *        em {@code monitor.search_interval_minutes}, por monitor
 * @param batchSize    quantos monitores um ciclo processa. Limita o tempo do
 *        ciclo e evita que uma fila grande monopolize a thread
 * @param retryDelay   quando uma varredura falha, o monitor volta a fila depois
 *        deste tempo em vez de esperar o intervalo inteiro — mas nao antes,
 *        para nao martelar uma fonte que ja esta com problema
 */
@ConfigurationProperties(prefix = "flightmonitor.scheduler")
public record SchedulerProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        Duration retryDelay) {

    public SchedulerProperties {
        pollInterval = pollInterval == null ? Duration.ofMinutes(1) : pollInterval;
        retryDelay = retryDelay == null ? Duration.ofMinutes(15) : retryDelay;
        if (batchSize < 1) {
            batchSize = 10;
        }
    }
}
