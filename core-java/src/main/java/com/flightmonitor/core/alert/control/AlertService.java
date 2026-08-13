package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.Alert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.control.SearchOutcome;
import com.flightmonitor.core.stats.control.FlightScore;
import com.flightmonitor.core.stats.control.FlightScoreService;
import com.flightmonitor.core.stats.control.FonteDeStats;
import com.flightmonitor.core.stats.control.PriceAnomaly;
import com.flightmonitor.core.stats.control.PriceAnomalyService;
import com.flightmonitor.core.stats.control.RouteStats;
import com.flightmonitor.core.stats.control.RouteStatsService;

/**
 * Decide quando vale a pena incomodar, e cria os alertas pendentes.
 *
 * <p>Esta classe define se o sistema sera usado ou desinstalado. Alertar demais
 * treina o usuario a ignorar a notificacao — e um sistema ignorado e pior que
 * um sistema desligado, porque da falsa sensacao de cobertura.
 *
 * <p>Aqui apenas <b>criamos</b> os alertas com status {@code PENDING}. O envio
 * e responsabilidade do {@code NotificationService} (etapa E1.11): separar
 * decidir de entregar permite testar a regra sem mandar mensagem, e reenviar
 * uma entrega falha sem re-decidir.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final BigDecimal CEM = new BigDecimal("100");

    /** Quantos alertas passados o anti-spam examina. O cooldown mantem esse numero baixo. */
    private static final int JANELA_DE_HISTORICO = 20;

    private final AlertRepository alertas;
    private final MonitorRepository monitores;
    private final PriceObservationRepository observacoes;
    private final AlertMessageFormatter formatador;
    private final AlertProperties props;
    private final NotificationProperties notificacao;
    private final RouteStatsService estatisticas;
    private final PriceAnomalyService anomalias;
    private final FlightScoreService score;

    public AlertService(
            AlertRepository alertas,
            MonitorRepository monitores,
            PriceObservationRepository observacoes,
            AlertMessageFormatter formatador,
            AlertProperties props,
            NotificationProperties notificacao,
            RouteStatsService estatisticas,
            PriceAnomalyService anomalias,
            FlightScoreService score) {
        this.alertas = alertas;
        this.monitores = monitores;
        this.observacoes = observacoes;
        this.formatador = formatador;
        this.props = props;
        this.notificacao = notificacao;
        this.estatisticas = estatisticas;
        this.anomalias = anomalias;
        this.score = score;
    }

    @Transactional
    public AlertDecision avaliar(SearchOutcome resultado) {
        if (!resultado.temOportunidade()) {
            return AlertDecision.naoAlertar(
                    AlertDecision.Motivo.SEM_OPORTUNIDADE,
                    "nenhuma oferta abaixo do teto que se sustentasse");
        }

        // A camada 2 nao respondeu: o preco vem do cache, sem confirmacao.
        // Medimos divergencias de 61%, 69% e 81% entre cache e preco real —
        // alertar sobre isso seria quase sempre alarme falso. Ver D-041.
        if (resultado.camada2Degradada() && !props.alertarSemConfirmacao()) {
            return AlertDecision.naoAlertar(
                    AlertDecision.Motivo.SEM_CONFIRMACAO,
                    "preco nao confirmado: a camada 2 estava indisponivel");
        }

        Monitor monitor = monitores.findByIdComDestinatarios(resultado.monitorId()).orElseThrow();
        PriceObservation oferta = observacoes.findById(resultado.melhorObservacaoId()).orElseThrow();

        List<Recipient> destinatarios = monitor.getRecipients().stream()
                .filter(Recipient::isActive)
                .toList();

        if (destinatarios.isEmpty()) {
            return AlertDecision.naoAlertar(
                    AlertDecision.Motivo.SEM_DESTINATARIOS,
                    "o monitor nao tem destinatario ativo");
        }

        AlertDecision antiSpam = aplicarAntiSpam(monitor, oferta);
        if (!antiSpam.alertar()) {
            return antiSpam;
        }

        criarAlertas(monitor, oferta, destinatarios);

        log.info("monitor {}: alerta criado para {} destinatario(s) a {} ({})",
                monitor.getId(), destinatarios.size(), oferta.getPrice(), antiSpam.detalhe());

        // Preserva o motivo do anti-spam: saber que alertamos e menos util do
        // que saber POR QUE — se foi o primeiro alerta, se as datas eram novas
        // ou se houve queda relevante.
        return AlertDecision.alertar("%s; %d alerta(s) criado(s) a %s"
                .formatted(antiSpam.detalhe(), destinatarios.size(), oferta.getPrice()));
    }

    /**
     * As duas travas do anti-spam, que valem em conjunto.
     *
     * <ol>
     *   <li><b>Cooldown por monitor</b> — nao manda dois alertas do mesmo monitor
     *       em menos de N horas, mesmo para datas diferentes. Evita rajada quando
     *       varias datas ficam abaixo do teto ao mesmo tempo.</li>
     *   <li><b>Queda minima por data</b> — para a MESMA combinacao de ida e volta,
     *       so re-alerta se o preco caiu o percentual configurado. Preco de
     *       passagem oscila varias vezes ao dia; sem isso, cada centavo a menos
     *       viraria mensagem.</li>
     * </ol>
     */
    private AlertDecision aplicarAntiSpam(Monitor monitor, PriceObservation oferta) {
        List<Alert> anteriores = alertas.recentesEntregaveis(
                monitor.getId(), PageRequest.of(0, JANELA_DE_HISTORICO));

        if (anteriores.isEmpty()) {
            return AlertDecision.alertar("primeiro alerta deste monitor");
        }

        Instant limiteCooldown = Instant.now().minus(props.cooldown());
        Alert ultimo = anteriores.get(0);
        if (ultimo.getCreatedAt() != null && ultimo.getCreatedAt().isAfter(limiteCooldown)) {
            return AlertDecision.naoAlertar(
                    AlertDecision.Motivo.DENTRO_DO_COOLDOWN,
                    "ultimo alerta ha menos de " + props.cooldown().toHours() + "h");
        }

        BigDecimal ultimoPrecoDaData = ultimoPrecoAlertadoParaAsDatas(anteriores, oferta);
        if (ultimoPrecoDaData == null) {
            return AlertDecision.alertar("datas ainda nao alertadas");
        }

        BigDecimal quedaPercentual = quedaPercentual(ultimoPrecoDaData, oferta.getPrice());
        if (quedaPercentual.compareTo(props.quedaMinimaPercentual()) < 0) {
            return AlertDecision.naoAlertar(
                    AlertDecision.Motivo.QUEDA_INSUFICIENTE,
                    "de %s para %s e queda de %s%%, abaixo do minimo de %s%%".formatted(
                            ultimoPrecoDaData, oferta.getPrice(),
                            quedaPercentual, props.quedaMinimaPercentual()));
        }

        return AlertDecision.alertar(
                "queda de %s%% desde o ultimo alerta para estas datas".formatted(quedaPercentual));
    }

    private BigDecimal ultimoPrecoAlertadoParaAsDatas(List<Alert> anteriores, PriceObservation oferta) {
        return anteriores.stream()
                .map(Alert::getPriceObservation)
                .filter(Objects::nonNull)
                .filter(o -> Objects.equals(o.getDepartureDate(), oferta.getDepartureDate()))
                .filter(o -> Objects.equals(o.getReturnDate(), oferta.getReturnDate()))
                .map(PriceObservation::getPrice)
                .findFirst()
                .orElse(null);
    }

    /** Percentual de queda. Negativo quando o preco subiu. */
    private BigDecimal quedaPercentual(BigDecimal anterior, BigDecimal atual) {
        if (anterior == null || anterior.signum() <= 0) {
            return CEM;
        }
        return anterior.subtract(atual)
                .multiply(CEM)
                .divide(anterior, 2, RoundingMode.HALF_UP);
    }

    private void criarAlertas(Monitor monitor, PriceObservation oferta, List<Recipient> destinatarios) {
        AlertInsights analise = analisar(monitor, oferta);
        String mensagem = formatador.formatar(monitor, oferta, analise);

        List<Alert> novos = new ArrayList<>(destinatarios.size());
        for (Recipient destinatario : destinatarios) {
            // Uma linha por destinatario: se a entrega falhar para uma pessoa e
            // funcionar para as outras, isso precisa ficar registrado.
            Alert alerta = new Alert(monitor, oferta, destinatario, mensagem);
            // O alerta registra o que sabia no momento da decisao. Ver V5.
            alerta.registrarAnalise(
                    analise.nota(), analise.grau(), analise.quedaPercentual());
            // O canal fica gravado no alerta, e nao so na configuracao: trocar o
            // canal depois nao pode reescrever a historia de como um alerta
            // antigo foi entregue.
            alerta.setChannel(notificacao.canal());
            novos.add(alerta);
        }
        alertas.saveAll(novos);
    }

    /**
     * Reune o que a Fase 2 sabe sobre a oferta — etapa E2.4.
     *
     * <p>Base <b>CONFIRMADAS</b>, e nao TODAS. A oferta que chega aqui passou
     * pela camada 2, entao compara-la com estatistica de cache seria o erro que
     * a D-060 descreve: o cache subestima em 61% a 81%, e o preco real pareceria
     * caro. O efeito seria uma mensagem dizendo que uma oferta boa esta acima da
     * media.
     *
     * <p>O custo disso e que o enriquecimento <b>demora a aparecer</b>: so uma
     * observacao por varredura passa pela camada 2, entao a rota leva algumas
     * varreduras para acumular amostra confiavel. E o preco de nao chutar.
     *
     * <p>Nunca lanca. Uma falha na analise nao pode impedir o alerta — a
     * oportunidade e o que importa, e o enriquecimento e enfeite.
     */
    private AlertInsights analisar(Monitor monitor, PriceObservation oferta) {
        try {
            RouteStats ref = estatisticas.resumir(
                    oferta.getOrigin(), oferta.getDestination(), FonteDeStats.CONFIRMADAS);

            PriceAnomaly anomalia = anomalias.avaliar(oferta.getPrice(), ref);
            // Com o monitor: os pesos e a preferencia por voo direto sao dele,
            // e nao globais (E2.6).
            FlightScore nota = score.pontuar(oferta, FonteDeStats.CONFIRMADAS, monitor);

            return AlertInsights.de(anomalia, nota);
        } catch (RuntimeException e) {
            log.warn("nao foi possivel analisar a oferta {}: {}",
                    oferta.getId(), e.toString());
            return AlertInsights.vazio();
        }
    }
}
