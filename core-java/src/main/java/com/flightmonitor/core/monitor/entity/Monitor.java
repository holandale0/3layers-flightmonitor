package com.flightmonitor.core.monitor.entity;

import com.flightmonitor.core.monitor.entity.Preferencias;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.flightmonitor.core.recipient.entity.Recipient;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um criterio de busca que o sistema vigia periodicamente.
 *
 * <p>As regras de coerencia (IATA em maiusculas, origem diferente do destino,
 * janela de volta preenchida aos pares, etc.) sao garantidas por CHECK no banco.
 * Ver {@code V1__initial_schema.sql}.
 */
@Entity
@Table(name = "monitor")
@Getter
@Setter
@NoArgsConstructor
public class Monitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String label;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    /** Janela de ida: o monitor varre todos os dias entre estas duas datas. */
    @Column(name = "departure_window_start", nullable = false)
    private LocalDate departureWindowStart;

    @Column(name = "departure_window_end", nullable = false)
    private LocalDate departureWindowEnd;

    /** Janela de volta. Nula nos dois campos significa somente ida. */
    @Column(name = "return_window_start")
    private LocalDate returnWindowStart;

    @Column(name = "return_window_end")
    private LocalDate returnWindowEnd;

    /** Alternativa a janela de volta: permanencia em dias no destino. */
    @Column(name = "min_stay_days")
    private Short minStayDays;

    @Column(name = "max_stay_days")
    private Short maxStayDays;

    @Column(name = "max_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxPrice;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    /** Nulo significa qualquer numero de escalas. */
    @Column(name = "max_stops")
    private Short maxStops;

    @Column(nullable = false)
    private short passengers = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "search_interval_minutes", nullable = false)
    private int searchIntervalMinutes = 360;

    @Column(name = "last_searched_at")
    private Instant lastSearchedAt;

    /** Quando o scheduler deve varrer este monitor de novo. */
    @Column(name = "next_search_at", nullable = false)
    private Instant nextSearchAt = Instant.now();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at")
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "monitor_recipient",
            joinColumns = @JoinColumn(name = "monitor_id"),
            inverseJoinColumns = @JoinColumn(name = "recipient_id"))
    private Set<Recipient> recipients = new LinkedHashSet<>();

    // ---------------------------------------------------- preferencias (E2.6)

    /**
     * Penaliza escalas com mais forca na nota.
     *
     * <p>Diferente de {@code maxStops}, que e limite RIGIDO enviado a fonte:
     * aqui o voo com escala continua valendo, so vale menos.
     */
    @Column(name = "prefere_voo_direto", nullable = false)
    private boolean prefereVooDireto = false;

    /**
     * Companhias que este monitor nao quer, ja em maiuscula.
     *
     * <p>Tabela propria e nao lista separada por virgula: a camada 2 devolve
     * nomes por extenso, e uma virgula dentro do nome quebraria a lista em
     * silencio.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "monitor_avoided_airline",
            joinColumns = @JoinColumn(name = "monitor_id"))
    @Column(name = "airline", nullable = false, length = 60)
    private Set<String> avoidedAirlines = new LinkedHashSet<>();

    /** Pesos do Flight Score. Nulo = usa o global. Ver E2.3 e D-074. */
    @Column(name = "peso_preco")
    private Short pesoPreco;

    @Column(name = "peso_escalas")
    private Short pesoEscalas;

    @Column(name = "peso_duracao")
    private Short pesoDuracao;

    @Column(name = "peso_horario")
    private Short pesoHorario;

    /** Guarda ja normalizado: o CHECK do banco exige maiuscula sem espaco. */
    public void evitarCompanhia(String companhia) {
        String normalizada = Preferencias.normalizar(companhia);
        if (!normalizada.isEmpty()) {
            avoidedAirlines.add(normalizada);
        }
    }

    public boolean temPreferenciaDePeso() {
        return pesoPreco != null || pesoEscalas != null
                || pesoDuracao != null || pesoHorario != null;
    }

    public void addRecipient(Recipient recipient) {
        recipients.add(recipient);
    }

    public void removeRecipient(Recipient recipient) {
        recipients.remove(recipient);
    }

    /** Marca a varredura como feita e agenda a proxima conforme o intervalo. */
    public void registrarBusca(Instant momento) {
        this.lastSearchedAt = momento;
        this.nextSearchAt = momento.plusSeconds(searchIntervalMinutes * 60L);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Monitor other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Monitor.class.hashCode();
    }
}
