package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertChannel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da entrega de alertas.
 *
 * @param canal canal ativo. {@code LOG} ate a etapa E1.12 trazer o WhatsApp —
 *        e permanece como valvula de escape se o WhatsApp cair
 * @param maxTentativas quantas vezes tentar entregar antes de marcar FAILED.
 *        So conta falha <b>transitoria</b>: falha permanente vai direto a FAILED
 * @param lote quantos alertas pendentes um despacho processa
 */
@ConfigurationProperties(prefix = "flightmonitor.notification")
public record NotificationProperties(AlertChannel canal, int maxTentativas, int lote) {

    public NotificationProperties {
        canal = canal == null ? AlertChannel.LOG : canal;
        if (maxTentativas < 1) {
            maxTentativas = 3;
        }
        if (lote < 1) {
            lote = 50;
        }
    }
}
