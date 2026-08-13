package com.flightmonitor.core.stats.control;

/**
 * Para onde o preco da rota esta indo — etapa E2.5.
 *
 * <p>A pergunta que isto responde e a que o usuario realmente faz: <b>compro
 * agora ou espero?</b> As etapas anteriores diziam se o preco de hoje e bom
 * comparado ao passado; esta diz se o passado recente esta melhorando ou
 * piorando.
 */
public enum DirecaoDaTendencia {

    /**
     * Nao ha serie suficiente para dizer.
     *
     * <p>Distinto de {@link #ESTAVEL}: la medimos e a variacao e pequena, aqui
     * nao ha o que medir. Colapsar os dois faria o sistema afirmar "estavel"
     * sobre uma rota observada uma vez.
     */
    SEM_DADOS,

    /** Variacao dentro do limiar: nao ha movimento que valha mencionar. */
    ESTAVEL,

    /** O preco vem caindo — esperar tem chance de melhorar. */
    CAINDO,

    /** O preco vem subindo — esperar tende a custar mais caro. */
    SUBINDO;

    /** Vale mencionar num alerta? "Estavel" e "nao sei" nao viram mensagem. */
    public boolean vaiNaMensagem() {
        return this == CAINDO || this == SUBINDO;
    }
}
