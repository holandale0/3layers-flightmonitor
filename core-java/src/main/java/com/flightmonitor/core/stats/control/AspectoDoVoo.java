package com.flightmonitor.core.stats.control;

/**
 * O que entra na nota de um voo — etapa E2.3.
 *
 * <p>Quatro aspectos, e nao um numero magico. Uma nota unica sem decomposicao e
 * inauditavel: quando ela sai 62, ninguem sabe se foi o preco, a escala ou o
 * voo de madrugada — e sem saber isso a nota nao ajuda a decidir nada.
 */
public enum AspectoDoVoo {

    /** Onde o preco cai na distribuicao historica da rota. */
    PRECO,

    /** Numero de escalas. Direto vale muito mais do que uma escala. */
    ESCALAS,

    /**
     * Duracao comparada a melhor ja vista NA MESMA ROTA.
     *
     * <p>Limite absoluto em horas seria inutil: dez horas e otimo para
     * Sao Paulo-Lisboa e absurdo para Sao Paulo-Curitiba.
     */
    DURACAO,

    /**
     * Horario de partida.
     *
     * <p>O criterio embutido aqui e uma preferencia media, nao uma verdade —
     * ha quem prefira voo de madrugada por ser mais barato e vazio. A E2.6 vai
     * permitir que o monitor sobrescreva isto.
     */
    HORARIO
}
