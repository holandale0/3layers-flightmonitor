package com.flightmonitor.core.recipient.entity;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quem recebe os alertas.
 *
 * <p><b>Pelo menos um contato</b>, e nao necessariamente os dois: desde a E4.6
 * um destinatario pode existir so com e-mail, so com telefone, ou com ambos. O
 * CHECK {@code recipient_tem_algum_contato} impede o estado sem sentido — uma
 * pessoa cadastrada para receber alertas sem nenhuma forma de recebe-los.
 */
@Entity
@Table(name = "recipient")
@Getter
@Setter
@NoArgsConstructor
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Formato E.164, ex: +5511999998888. Validado por CHECK no banco.
     *
     * <p>Opcional desde a E4.6. Era obrigatorio quando WhatsApp era o unico
     * canal; exigir um numero de quem so recebe por e-mail produziria dado
     * mentiroso — e um numero falso que um dia recebe mensagem de verdade.
     */
    @Column(name = "phone_e164", length = 16, unique = true)
    private String phoneE164;

    /** Endereco para o canal EMAIL (E4.6). Nulo quando so recebe por WhatsApp. */
    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    // createdAt e updatedAt sao gerados pelo banco (DEFAULT now() e trigger).
    // Ver D-017: o banco e a autoridade sobre esses valores.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at")
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Recipient(String name, String phoneE164) {
        this(name, phoneE164, null);
    }

    public Recipient(String name, String phoneE164, String email) {
        this.name = name;
        this.phoneE164 = phoneE164;
        this.email = email;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Recipient other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Recipient.class.hashCode();
    }
}
