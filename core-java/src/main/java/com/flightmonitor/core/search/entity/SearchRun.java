package com.flightmonitor.core.search.entity;

import java.time.Instant;


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
 * Uma execucao de varredura.
 *
 * <p>Existe para responder "com que frequencia esta fonte falha?". Sem isso,
 * a queda de um provider so apareceria como ausencia de alertas — silenciosa
 * e dificil de diagnosticar.
 */
@Entity
@Table(name = "search_run")
@Getter
@Setter
@NoArgsConstructor
public class SearchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SearchStatus status = SearchStatus.RUNNING;

    /**
     * Preenchido pelo Java, e nao pelo banco.
     *
     * <p>Parece detalhe e nao e: o {@code finished_at} vem do relogio da JVM, e
     * antes daqui o {@code started_at} vinha do {@code clock_timestamp()} do
     * PostgreSQL. Dois relogios diferentes — o do host e o do container — com
     * dessincronia de microssegundos bastavam para uma execucao rapida terminar
     * "antes" de comecar, violando o CHECK e derrubando a varredura. Ver
     * BUG-010.
     *
     * <p>O DEFAULT continua no schema, para uma linha inserida fora da
     * aplicacao. A licao do BUG-002 permanece respeitada: isto e um instante de
     * evento lido no momento em que o evento acontece, e nao o inicio da
     * transacao.
     */
    @Column(name = "started_at")
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "observations_count", nullable = false)
    private int observationsCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    public SearchRun(Monitor monitor, PriceSource source) {
        this.monitor = monitor;
        this.source = source;
        this.startedAt = Instant.now();
    }

    public void concluir(SearchStatus status, int observacoes) {
        this.status = status;
        this.observationsCount = observacoes;
        this.finishedAt = Instant.now();
    }

    public void falhar(String erro) {
        this.status = SearchStatus.FAILED;
        this.errorMessage = erro;
        this.finishedAt = Instant.now();
    }
}
