package com.flightmonitor.core.search.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.flightmonitor.core.monitor.entity.Monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Um preco observado. E a tabela mais importante do sistema — dela sai todo o
 * historico e toda a inteligencia da Fase 2.
 *
 * <p><b>origin e destination sao denormalizados de proposito.</b> O que o sistema
 * aprende sobre uma rota pertence a rota, nao ao monitor: apagar um monitor nao
 * pode apagar meses de historico, e dois monitores da mesma rota devem somar
 * observacoes. Por isso {@code monitor} e opcional (ON DELETE SET NULL no banco).
 * Ver D-016 em docs/DECISOES.md.
 */
@Entity
@Table(name = "price_observation")
@Getter
@Setter
@NoArgsConstructor
public class PriceObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo quando o monitor que originou a observacao foi apagado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_run_id")
    private SearchRun searchRun;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Column(length = 80)
    private String airline;

    private Short stops;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // Horario local do aeroporto — por isso LocalDateTime, sem fuso.
    @Column(name = "departure_at")
    private LocalDateTime departureAt;

    @Column(name = "arrival_at")
    private LocalDateTime arrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceSource source;

    /** true quando a camada 2 confirmou o preco apontado pela camada 1. */
    @Column(nullable = false)
    private boolean confirmed = false;

    @Generated(event = EventType.INSERT)
    @Column(name = "observed_at")
    private Instant observedAt;

    public PriceObservation(
            Monitor monitor,
            String origin,
            String destination,
            LocalDate departureDate,
            BigDecimal price,
            PriceSource source) {
        this.monitor = monitor;
        this.origin = origin;
        this.destination = destination;
        this.departureDate = departureDate;
        this.price = price;
        this.source = source;
    }
}
