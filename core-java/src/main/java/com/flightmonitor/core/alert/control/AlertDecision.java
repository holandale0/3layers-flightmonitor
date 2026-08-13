package com.flightmonitor.core.alert.control;

/**
 * Por que o sistema alertou — ou por que se calou.
 *
 * <p>O motivo e tao importante quanto a decisao. Sem ele, "nao recebi alerta"
 * seria indistinguivel de "nao houve oportunidade", "o preco nao caiu o
 * suficiente" e "o sistema esta quebrado".
 */
public record AlertDecision(boolean alertar, Motivo motivo, String detalhe) {

    public enum Motivo {
        /** Alerta disparado. */
        ALERTADO,

        /** Nenhuma oferta abaixo do teto, ou o candidato nao se sustentou. */
        SEM_OPORTUNIDADE,

        /**
         * A camada 2 nao respondeu e o preco veio do cache, sem confirmacao.
         * Segurar e mais seguro que arriscar alarme falso — ver D-041.
         */
        SEM_CONFIRMACAO,

        /** Ja alertamos este monitor ha pouco tempo. */
        DENTRO_DO_COOLDOWN,

        /** O preco caiu, mas nao o bastante para justificar nova mensagem. */
        QUEDA_INSUFICIENTE,

        /** O monitor nao tem nenhum destinatario ativo para receber. */
        SEM_DESTINATARIOS
    }

    public static AlertDecision alertar(String detalhe) {
        return new AlertDecision(true, Motivo.ALERTADO, detalhe);
    }

    public static AlertDecision naoAlertar(Motivo motivo, String detalhe) {
        return new AlertDecision(false, motivo, detalhe);
    }
}
