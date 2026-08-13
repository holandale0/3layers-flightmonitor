package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nota de 0 a 100 para um voo — etapa E2.3.
 *
 * <h2>Uma nota composta mente com facilidade</h2>
 *
 * Qualquer nota que junta preco, escalas, duracao e horario embute uma opiniao
 * sobre quanto cada coisa vale. Essa opiniao pode estar errada para quem le, e
 * um numero sozinho esconde isso atras de uma aparencia de objetividade.
 *
 * <p>Por isso este objeto nunca devolve so o numero. Ele carrega os
 * {@link #componentes()}, cada um com a propria nota e o proprio peso, e a
 * {@link #cobertura()} — quanto do peso total pode ser realmente avaliado.
 *
 * <h2>Cobertura: a parte que quase todo sistema esquece</h2>
 *
 * Observacoes da camada 1 nao trazem duracao nem horario. Uma nota calculada so
 * com preco e escalas <b>nao e comparavel</b> com uma calculada com tudo, e
 * apresentar as duas como "82" e "82" seria mentira por omissao.
 *
 * <p>A nota e renormalizada sobre o peso disponivel, e a cobertura diz de quanto
 * ela veio.
 *
 * @param nota 0 a 100, ou {@code null} quando nao houve nada avaliavel
 * @param cobertura fracao do peso total que pode ser avaliada, de 0 a 1
 * @param confiavel se a nota se sustenta: exige estatistica confiavel para o
 *        preco e cobertura acima do minimo configurado
 */
public record FlightScore(
        Integer nota,
        boolean confiavel,
        BigDecimal cobertura,
        List<ComponenteDoScore> componentes,
        String explicacao) {

    public FlightScore {
        componentes = componentes == null ? List.of() : List.copyOf(componentes);
    }

    public static FlightScore indisponivel(String motivo) {
        return new FlightScore(null, false, BigDecimal.ZERO, List.of(), motivo);
    }

    public boolean temNota() {
        return nota != null;
    }

    /**
     * Um aspecto avaliado.
     *
     * @param nota {@code null} quando o dado nao existe na observacao. Nulo e
     *        diferente de zero: zero e "avaliei e esta pessimo", nulo e "nao
     *        tenho como saber"
     * @param peso peso configurado, mantido aqui mesmo quando a nota e nula —
     *        e o que permite ver quanto da avaliacao ficou de fora
     */
    public record ComponenteDoScore(
            AspectoDoVoo aspecto,
            Integer nota,
            int peso,
            String detalhe) {

        public boolean avaliado() {
            return nota != null;
        }
    }
}
