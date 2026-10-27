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

    /**
     * Notificacao no aplicativo Android, pelo Firebase Cloud Messaging (V11).
     *
     * <p>Passa pelo Google, mas sem template para aprovar e sem caixa de spam:
     * as duas coisas que ja atrasaram ou esconderam alerta nos outros canais.
     * Ver D-113.
     */
    PUSH,

    /** Adaptador de desenvolvimento: apenas registra no log (etapa E1.11). */
    LOG
}
