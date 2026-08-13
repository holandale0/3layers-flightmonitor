package com.flightmonitor.core.stats.entity;

/**
 * Quao incomum e um preco, para baixo — etapa E2.2.
 *
 * <p>Os graus sao ordenados e <b>excludentes</b>: um preco recebe exatamente um.
 * A ordem de avaliacao vai do mais forte para o mais fraco, para que "menor
 * preco ja visto" nao seja rebaixado a "entre os 25% mais baratos", que tambem
 * seria verdade e diria menos.
 *
 * <p>Só olhamos para baixo. Preco alto nao e anomalia acionavel — o sistema
 * existe para avisar de oportunidade, e ninguem quer receber mensagem dizendo
 * que a passagem esta cara.
 */
public enum GrauDeAnomalia {

    /**
     * Nao ha historico suficiente para dizer o que e normal.
     *
     * <p>Diferente de {@link #NORMAL}: aqui nao sabemos, la sabemos e o preco
     * nao tem nada de especial. Confundir os dois faria a E2.4 escrever
     * "preco dentro do normal" sobre uma rota que nunca foi medida.
     */
    SEM_DADOS,

    /** Dentro da faixa comum da rota. Nada a comentar. */
    NORMAL,

    /** Entre os 25% mais baratos ja observados na janela. */
    BOM,

    /**
     * Abaixo do limite estatistico de valor atipico.
     *
     * <p>Usa a regra de Tukey — {@code p25 - 1.5 x (p75 - p25)} —, a mesma que
     * desenha os "bigodes" de um boxplot. Escolhida por ser robusta: baseada em
     * quartis, ela nao se desloca quando um preco absurdo entra na amostra, ao
     * contrario de qualquer regra baseada em media e desvio.
     */
    EXCELENTE,

    /** Menor preco da janela inteira. */
    RECORDE;

    /** Vale a pena mencionar num alerta? Base da mensagem enriquecida (E2.4). */
    public boolean vaiNaMensagem() {
        return this == BOM || this == EXCELENTE || this == RECORDE;
    }
}
