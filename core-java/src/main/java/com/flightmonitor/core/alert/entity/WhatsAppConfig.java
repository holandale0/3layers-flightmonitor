package com.flightmonitor.core.alert.entity;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuracao <b>nao-secreta</b> do canal WhatsApp — etapa E4.7.
 *
 * <p>O que identifica <i>qual</i> conta e <i>qual</i> template usar. Token de
 * acesso, app secret e verify token <b>nao moram aqui</b>: continuam no
 * ambiente, porque coluna de texto vai parar em {@code pg_dump}, em backup e em
 * qualquer log que serialize a entidade.
 *
 * <p><b>Uma linha, e so uma</b> ({@code CHECK (id = 1)}). O sistema e pessoal e
 * fala por um numero so; varias linhas criariam a pergunta "qual vale?", que nao
 * tem resposta boa.
 *
 * <p><b>Tabela vazia e um estado valido</b>, e significa "ninguem configurou pela
 * tela" — ai o valor do {@code .env} continua valendo. E o que mantem
 * funcionando, sem tocar em nada, toda instalacao anterior a esta etapa.
 */
@Entity
@Table(name = "whatsapp_config")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConfig {

    /** Sempre 1. O banco recusa qualquer outro valor. */
    public static final long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    /** Identificador Meta do numero remetente. <b>Nao</b> e um telefone. */
    @Column(name = "phone_number_id", length = 40)
    private String phoneNumberId;

    /** WABA dona do template. Ver BUG-009: template certo, conta errada. */
    @Column(name = "waba_id", length = 40)
    private String wabaId;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "template_language", nullable = false, length = 10)
    private String templateLanguage;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at")
    private Instant updatedAt;
}
