package com.flightmonitor.core.alert.entity;

/** Canal de entrega do alerta. Os nomes batem com o CHECK do banco. */
public enum AlertChannel {

    WHATSAPP,

    /**
     * E-mail por SMTP (etapa E4.6).
     *
     * <p>Entrou porque o WhatsApp se mostrou <b>bloqueavel por terceiro</b>: o
     * template ficou dias em analise na Meta, e nesse periodo o sistema varria,
     * confirmava, pontuava, decidia alertar — e nao conseguia avisar ninguem.
     * Ver D-097.
     */
    EMAIL,

    /** Adaptador de desenvolvimento: apenas registra no log (etapa E1.11). */
    LOG
}
