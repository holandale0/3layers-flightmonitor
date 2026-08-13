package com.flightmonitor.core.alert.control;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Regras de quando vale a pena incomodar.
 *
 * <p>Estes numeros decidem se o sistema sera usado ou desinstalado. Alertar
 * demais treina o usuario a ignorar a notificacao; alertar de menos faz perder
 * a passagem.
 *
 * @param quedaMinimaPercentual queda exigida para re-alertar sobre a MESMA
 *        combinacao de datas. Preco de passagem oscila varias vezes ao dia;
 *        sem isso, cada centavo a menos viraria mensagem
 * @param cooldown intervalo minimo entre dois alertas do mesmo monitor,
 *        independentemente das datas. Evita rajada quando varias datas ficam
 *        abaixo do teto ao mesmo tempo
 * @param alertarSemConfirmacao se deve alertar quando a camada 2 esta fora do
 *        ar. Padrao {@code false} — ver D-041
 */
@ConfigurationProperties(prefix = "flightmonitor.alert")
public record AlertProperties(
        BigDecimal quedaMinimaPercentual,
        Duration cooldown,
        boolean alertarSemConfirmacao) {

    public AlertProperties {
        quedaMinimaPercentual = quedaMinimaPercentual == null
                ? new BigDecimal("5") : quedaMinimaPercentual;
        cooldown = cooldown == null ? Duration.ofHours(12) : cooldown;
    }
}
