package com.flightmonitor.core.search.control.client;

/**
 * O worker nao respondeu, ou respondeu com falha.
 *
 * <p>Lancada apenas na camada 1. Sem preco nao ha varredura, entao a execucao
 * deve ser marcada como FAILED em {@code search_run} e tentada de novo no
 * proximo ciclo.
 */
public class WorkerUnavailableException extends RuntimeException {

    public WorkerUnavailableException(String mensagem) {
        super(mensagem);
    }

    public WorkerUnavailableException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
