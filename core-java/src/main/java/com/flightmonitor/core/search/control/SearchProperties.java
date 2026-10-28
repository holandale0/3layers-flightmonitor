package com.flightmonitor.core.search.control;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da varredura.
 *
 * @param maxConfirmacoes quantos candidatos da camada 1 sao levados a camada 2
 *        por varredura. Confirmar todos seria caro e lento — cada confirmacao
 *        e uma consulta ao vivo ao Google, de ~1s, sujeita a bloqueio por
 *        excesso de requisicoes. O alerta trata da melhor oportunidade, entao
 *        confirmar a mais barata basta.
 * @param reserva quando a camada 2 consulta direto, sem candidato (D-114)
 */
@ConfigurationProperties(prefix = "flightmonitor.search")
public record SearchProperties(int maxConfirmacoes, Reserva reserva) {

    public SearchProperties {
        if (maxConfirmacoes < 1) {
            maxConfirmacoes = 1;
        }
        if (reserva == null) {
            reserva = new Reserva(true, 0, null);
        }
    }

    /**
     * A camada 2 como reserva da camada 1 (D-114).
     *
     * <p>O cache da camada 1 so tem as datas que alguem pesquisou recentemente
     * no Aviasales. Para uma janela que ninguem pesquisou, ele volta vazio para
     * sempre — e o monitor fica "funcionando" sem enxergar nada. Medido em
     * GRU→BEL, 01 a 05/12: zero precos na camada 1, R$ 891 a R$ 986 no Google.
     *
     * @param enabled  desligavel sem deploy, se o Google comecar a bloquear
     * @param maxDias  janela mais longa que ainda e consultada dia a dia. Cada
     *        dia e uma consulta ao vivo; uma janela de 30 dias seriam 30
     * @param intervalo tempo minimo entre duas consultas da reserva no mesmo
     *        monitor. O monitor varre de hora em hora; a reserva, nao — o
     *        Google bloqueia quem pergunta demais, e preco de passagem nao
     *        muda tanto em 3 horas
     */
    public record Reserva(boolean enabled, int maxDias, Duration intervalo) {

        public Reserva {
            if (maxDias < 1) {
                maxDias = 7;
            }
            if (intervalo == null || intervalo.isNegative()) {
                intervalo = Duration.ofHours(3);
            }
        }
    }
}
