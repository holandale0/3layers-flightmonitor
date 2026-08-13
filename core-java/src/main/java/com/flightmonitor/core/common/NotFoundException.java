package com.flightmonitor.core.common;

/** Recurso inexistente. Traduzido para HTTP 404 pelo {@link ApiExceptionHandler}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String recurso, Long id) {
        super("%s %d nao encontrado".formatted(recurso, id));
    }

    public NotFoundException(String mensagem) {
        super(mensagem);
    }
}
