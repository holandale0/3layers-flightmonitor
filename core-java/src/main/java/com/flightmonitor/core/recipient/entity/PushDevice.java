package com.flightmonitor.core.recipient.entity;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um aparelho com o aplicativo instalado, que recebe alertas por push (V11).
 *
 * <p>E um contato do destinatario, como o telefone e o e-mail. A diferenca e que
 * nasce do proprio aplicativo — que pede o token ao Firebase e se apresenta ao
 * core — e nao de alguem digitando num formulario.
 */
@Entity
@Table(name = "push_device")
@Getter
@Setter
@NoArgsConstructor
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Recipient recipient;

    /** Token do Firebase Cloud Messaging. Muda quando o app e reinstalado. */
    @Column(nullable = false, unique = true)
    private String token;

    /** Como o aparelho aparece na tela, ex: "Galaxy S23". */
    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    /** Preenchido quando o Firebase recusou o token e o aparelho foi desligado. */
    @Column(name = "last_error")
    private String lastError;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at")
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at")
    private Instant updatedAt;

    public PushDevice(Recipient recipient, String token, String name) {
        this.recipient = recipient;
        this.token = token;
        this.name = name;
    }

    /**
     * Desliga o aparelho guardando o motivo.
     *
     * <p>Desligar, e nao apagar: quem abrir a tela precisa ver que o celular
     * existe e por que parou de receber, ou vai achar que o sistema esqueceu.
     */
    public void desativar(String motivo) {
        this.active = false;
        this.lastError = motivo;
    }

    /** O app se apresentou de novo: o token voltou a valer. */
    public void reativar(Recipient dono, String nome) {
        this.recipient = dono;
        this.name = nome;
        this.active = true;
        this.lastError = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof PushDevice other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return PushDevice.class.hashCode();
    }
}
