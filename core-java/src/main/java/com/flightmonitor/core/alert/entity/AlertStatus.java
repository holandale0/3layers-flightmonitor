package com.flightmonitor.core.alert.entity;

/**
 * Situacao da entrega de um alerta. Os nomes batem com o CHECK do banco.
 *
 * <p>A separacao entre {@link #ACCEPTED} e {@link #SENT} e a licao do BUG-007:
 * a Graph API devolveu {@code wamid} e {@code accepted} para quatro mensagens
 * que nunca chegaram. Um numero de protocolo nao e comprovante de entrega, e
 * tratar um como o outro faz o historico mentir justamente quando ha problema.
 * Ver D-053.
 */
public enum AlertStatus {

    /** Criado, ainda nao despachado. E o unico status que volta para a fila. */
    PENDING,

    /**
     * O provedor recebeu e devolveu um identificador. <b>Nao sabemos se chegou.</b>
     *
     * <p>So existe para canais de confirmacao assincrona — hoje, o WhatsApp.
     * Sai deste estado quando o webhook informa entrega ou falha.
     */
    ACCEPTED,

    /**
     * Entregue de verdade.
     *
     * <p>Para o WhatsApp, significa que o webhook confirmou a chegada no
     * aparelho. Para canais de confirmacao sincrona, como o LOG, significa que
     * a entrega aconteceu — ali nao ha intermediario que possa mentir.
     */
    SENT,

    /** Nao foi entregue, e o motivo esta em {@code error_message}. */
    FAILED
}
