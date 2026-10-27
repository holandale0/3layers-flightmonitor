package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertChannel;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da entrega de alertas.
 *
 * @param canais canais ativos, em paralelo (D-113). Cada destinatario recebe
 *        por todos os que o alcancam — app e e-mail ao mesmo tempo, por
 *        exemplo. {@code LOG} quando vazio, e continua sendo a valvula de
 *        escape se os outros cairem
 * @param maxTentativas quantas vezes tentar entregar antes de marcar FAILED.
 *        So conta falha <b>transitoria</b>: falha permanente vai direto a FAILED
 * @param lote quantos alertas pendentes um despacho processa
 */
@ConfigurationProperties(prefix = "flightmonitor.notification")
public record NotificationProperties(List<AlertChannel> canais, int maxTentativas, int lote) {

    public NotificationProperties {
        // distinct: "EMAIL,EMAIL" no .env nao pode virar dois e-mails iguais.
        canais = canais == null || canais.isEmpty()
                ? List.of(AlertChannel.LOG)
                : canais.stream().distinct().toList();
        if (maxTentativas < 1) {
            maxTentativas = 3;
        }
        if (lote < 1) {
            lote = 50;
        }
    }
}
