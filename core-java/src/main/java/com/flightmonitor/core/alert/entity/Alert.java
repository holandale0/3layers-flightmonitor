package com.flightmonitor.core.alert.entity;


import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.search.entity.PriceObservation;

import com.flightmonitor.core.stats.entity.GrauDeAnomalia;

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
 * Um envio de alerta a um destinatario.
 *
 * <p>Uma linha por destinatario, e nao por oportunidade: se tres pessoas
 * acompanham o mesmo monitor, o mesmo preco gera tres linhas. E o que permite
 * saber que a entrega falhou para uma pessoa e funcionou para as outras.
 */
@Entity
@Table(name = "alert")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_observation_id")
    private PriceObservation priceObservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertChannel channel = AlertChannel.WHATSAPP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.PENDING;

    @Column(nullable = false)
    private String message;

    /** Identificador devolvido pela Meta, para rastrear a entrega. */
    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "error_message")
    private String errorMessage;

    /** Quando entregamos a mensagem ao provedor. Nao e prova de que chegou. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** Quando o provedor confirmou entrega no aparelho. Nulo = nao confirmada. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Quando o destinatario abriu. Depende de confirmacao de leitura ligada. */
    @Column(name = "read_at")
    private Instant readAt;

    /** Quantas vezes tentamos entregar. Limita a retentativa de falha transitoria. */
    @Column(nullable = false)
    private int attempts = 0;

    /**
     * O que a Fase 2 sabia quando este alerta foi criado (E2.4).
     *
     * <p>Gravado, e nao recalculado na leitura: um numero recalculado meses
     * depois seria diferente do que motivou o alerta, e o historico passaria a
     * mentir sobre o proprio passado.
     *
     * <p>Nulo quando nao havia base — e diferente de zero, que seria "nota
     * zero".
     */
    // Short e nao Integer: a coluna e smallint, e a escala vai so ate 100. O
    // projeto ja modela assim o numero de escalas, pela mesma razao.
    @Column(name = "flight_score")
    private Short flightScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_grade", length = 20)
    private GrauDeAnomalia anomalyGrade;

    @Column(name = "anomaly_drop_pct")
    private BigDecimal anomalyDropPct;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Guarda a analise da E2.4. Nulos quando nao havia base para opinar.
     *
     * <p>Recebe os valores, e nao o objeto de analise do controle. A primeira
     * versao recebia {@code AlertInsights}, e a entidade passava a depender de
     * um record de caso de uso — inversao de dependencia que o teste de
     * arquitetura pegou. Entidade e o que o sistema lembra; ela nao precisa
     * conhecer quem produziu o numero.
     */
    public void registrarAnalise(Integer nota, GrauDeAnomalia grau, BigDecimal quedaPercentual) {
        this.flightScore = nota == null ? null : nota.shortValue();
        this.anomalyGrade = grau;
        this.anomalyDropPct = quedaPercentual;
    }

    public Alert(Monitor monitor, PriceObservation observation, Recipient recipient, String message) {
        this.monitor = monitor;
        this.priceObservation = observation;
        this.recipient = recipient;
        this.message = message;
    }

    /**
     * Entrega concluida e certa, sem intermediario que possa desmentir.
     *
     * <p>Para canais de confirmacao sincrona — hoje so o LOG. Canais que
     * dependem de webhook usam {@link #marcarAceito(String)}.
     */
    public void marcarEnviado(String providerMessageId) {
        this.status = AlertStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
        this.deliveredAt = this.sentAt;
        this.errorMessage = null;
    }

    /**
     * O provedor aceitou e devolveu um identificador. Ainda nao chegou.
     *
     * <p>Sai da fila de despacho — reenviar significaria mensagem repetida no
     * aparelho de quem recebe —, mas nao conta como entregue. Quem resolve a
     * duvida e o webhook.
     */
    public void marcarAceito(String providerMessageId) {
        this.status = AlertStatus.ACCEPTED;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * O webhook confirmou a entrega.
     *
     * @return {@code true} se este aviso mudou algo
     */
    public boolean marcarEntregue(Instant quando) {
        if (this.deliveredAt != null) {
            // A Meta reenvia o mesmo aviso quando nao recebe 200 rapido o
            // bastante. Idempotencia aqui evita historico reescrito a cada
            // repeticao.
            return false;
        }
        this.deliveredAt = quando;
        this.status = AlertStatus.SENT;
        this.errorMessage = null;
        return true;
    }

    /**
     * O webhook informou leitura.
     *
     * <p>Leitura implica entrega, e os dois avisos <b>nao chegam em ordem
     * garantida</b>. Se a leitura vier primeiro, ela tambem confirma a entrega —
     * usando o proprio instante, que e o mais preciso que temos naquele momento.
     *
     * @return {@code true} se este aviso mudou algo
     */
    public boolean marcarLido(Instant quando) {
        boolean mudou = false;

        if (this.deliveredAt == null) {
            this.deliveredAt = quando;
            this.status = AlertStatus.SENT;
            this.errorMessage = null;
            mudou = true;
        }
        if (this.readAt == null) {
            this.readAt = quando;
            mudou = true;
        }
        return mudou;
    }

    /**
     * O webhook informou que a entrega falhou.
     *
     * <p>E o caso do BUG-007: mensagem aceita, wamid devolvido, e a entrega
     * recusada depois por restricao de pais. Sem isto, o alerta ficaria
     * ACCEPTED para sempre e o motivo nao existiria em lugar nenhum.
     *
     * <p>Nao sobrescreve uma entrega ja confirmada: a Meta nao envia os dois
     * para a mesma mensagem, e se enviasse, a confirmacao de chegada e o fato
     * mais forte.
     *
     * @return {@code true} se este aviso mudou algo
     */
    public boolean marcarFalhaDeEntrega(String erro) {
        if (this.deliveredAt != null) {
            return false;
        }
        this.status = AlertStatus.FAILED;
        this.errorMessage = erro;
        return true;
    }

    public void marcarFalha(String erro) {
        this.status = AlertStatus.FAILED;
        this.errorMessage = erro;
    }

    /**
     * Registra uma falha transitoria: continua PENDING enquanto houver tentativa.
     *
     * <p>Sem o limite, um canal permanentemente quebrado ficaria em laco e um
     * numero invalido seria retentado para sempre.
     *
     * @return {@code true} se ainda vai tentar de novo
     */
    public boolean registrarTentativaFalha(String erro, int maxTentativas) {
        this.attempts++;
        this.errorMessage = erro;

        if (this.attempts >= maxTentativas) {
            this.status = AlertStatus.FAILED;
            this.errorMessage = "%s (desistindo apos %d tentativas)".formatted(erro, this.attempts);
            return false;
        }
        return true;
    }
}
