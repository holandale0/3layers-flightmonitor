package com.flightmonitor.core.common;

/**
 * Estado atual impede a operacao — por exemplo, cadastrar um destinatario com
 * telefone ja existente. Traduzido para HTTP 409.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
