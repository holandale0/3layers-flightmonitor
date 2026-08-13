package com.flightmonitor.core.alert.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de seguranca da entrega.
 *
 * <p>O caminho normal e a entrega imediata, logo apos criar o alerta. Esta
 * varredura existe para os casos em que isso nao bastou:
 *
 * <ul>
 *   <li>a aplicacao caiu entre criar o alerta e entrega-lo;</li>
 *   <li>a entrega falhou de forma transitoria e ha tentativa restante;</li>
 *   <li>o canal estava fora do ar e voltou.</li>
 * </ul>
 *
 * <p>Sem ela, um alerta pendente ficaria parado no banco para sempre — e o
 * usuario nunca saberia que houve uma oportunidade.
 *
 * <p>Segue as mesmas duas licoes do scheduler de varredura: {@code fixedDelay}
 * para os ciclos nao se empilharem, e captura de excecao porque erro que escapa
 * de um {@code @Scheduled} cancela o agendamento em definitivo.
 */
@Component
@ConditionalOnProperty(
        name = "flightmonitor.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationService notificacao;

    public NotificationScheduler(NotificationService notificacao) {
        this.notificacao = notificacao;
    }

    @Scheduled(
            fixedDelayString = "${flightmonitor.notification.sweep-interval:PT2M}",
            initialDelayString = "${flightmonitor.notification.initial-delay:PT45S}")
    public void entregarPendentes() {
        try {
            DispatchResult resultado = notificacao.despacharPendentes();
            if (!resultado.ocioso()) {
                log.info("varredura de pendentes: {}", resultado);
            }
        } catch (RuntimeException e) {
            log.error("varredura de alertas pendentes falhou; o agendamento continua", e);
        }
    }
}
