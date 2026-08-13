package com.flightmonitor.core.alert.control;

/**
 * Resultado de um despacho de alertas.
 *
 * <p>{@code falhas} e {@code retentar} sao numeros diferentes de proposito:
 * uma falha definitiva exige investigacao, uma que sera retentada nao.
 */
public record DispatchResult(int reivindicados, int entregues, int falhas, int retentar) {

    public static DispatchResult vazio() {
        return new DispatchResult(0, 0, 0, 0);
    }

    public boolean ocioso() {
        return reivindicados == 0;
    }
}
