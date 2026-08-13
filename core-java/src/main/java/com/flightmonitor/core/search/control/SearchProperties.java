package com.flightmonitor.core.search.control;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da varredura.
 *
 * @param maxConfirmacoes quantos candidatos da camada 1 sao levados a camada 2
 *        por varredura. Confirmar todos seria caro e lento — cada confirmacao
 *        e uma consulta ao vivo ao Google, de ~1s, sujeita a bloqueio por
 *        excesso de requisicoes. O alerta trata da melhor oportunidade, entao
 *        confirmar a mais barata basta.
 */
@ConfigurationProperties(prefix = "flightmonitor.search")
public record SearchProperties(int maxConfirmacoes) {

    public SearchProperties {
        if (maxConfirmacoes < 1) {
            maxConfirmacoes = 1;
        }
    }
}
