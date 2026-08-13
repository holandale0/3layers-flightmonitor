package com.flightmonitor.core.search.control;

/**
 * Resultado de um ciclo do scheduler.
 *
 * <p>Existe para que o ciclo seja testavel e observavel. Sem retorno, a unica
 * forma de saber se o agendamento funciona seria ler log.
 *
 * <p>{@code oportunidades} e {@code alertados} sao numeros diferentes de
 * proposito: uma oportunidade pode nao virar alerta por anti-spam ou por falta
 * de confirmacao. Ver a diferenca e o que permite calibrar as regras.
 */
public record CycleResult(
        int reivindicados,
        int sucesso,
        int falha,
        int oportunidades,
        int alertados) {

    public static CycleResult vazio() {
        return new CycleResult(0, 0, 0, 0, 0);
    }

    public boolean ocioso() {
        return reivindicados == 0;
    }
}
