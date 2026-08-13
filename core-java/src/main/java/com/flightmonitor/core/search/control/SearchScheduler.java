package com.flightmonitor.core.search.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o ciclo de varredura periodicamente.
 *
 * <p>Esta classe e propositalmente magra: so agenda. Toda a logica esta em
 * {@link SearchCycleService}, que e testavel sem esperar o relogio.
 *
 * <p><b>{@code fixedDelay} e nao {@code fixedRate}:</b> com {@code fixedRate} o
 * Spring dispara a cada N segundos independentemente de o ciclo anterior ter
 * terminado. Como uma varredura pode levar dezenas de segundos, os ciclos se
 * empilhariam e varreriam os mesmos monitores em paralelo. Com
 * {@code fixedDelay}, a contagem so comeca depois que o ciclo anterior termina.
 *
 * <p>Desligavel por {@code flightmonitor.scheduler.enabled=false}: permite subir
 * a API sem o motor, sem gastar cota das fontes.
 */
@Component
@ConditionalOnProperty(
        name = "flightmonitor.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SearchScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchScheduler.class);

    private final SearchCycleService ciclo;

    public SearchScheduler(SearchCycleService ciclo) {
        this.ciclo = ciclo;
        log.info("scheduler de varredura ativo");
    }

    @Scheduled(
            fixedDelayString = "${flightmonitor.scheduler.poll-interval:PT1M}",
            initialDelayString = "${flightmonitor.scheduler.initial-delay:PT30S}")
    public void varrerMonitoresVencidos() {
        try {
            CycleResult resultado = ciclo.executarCiclo();
            if (!resultado.ocioso()) {
                log.debug("ciclo agendado: {}", resultado);
            }
        } catch (RuntimeException e) {
            // Excecao que escape aqui cancelaria o agendamento em definitivo:
            // o Spring nao reagenda uma tarefa que lancou. O motor pararia em
            // silencio e so a ausencia de alertas denunciaria.
            log.error("ciclo de varredura falhou; o agendamento continua", e);
        }
    }
}
