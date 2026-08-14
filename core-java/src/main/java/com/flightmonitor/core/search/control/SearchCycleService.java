package com.flightmonitor.core.search.control;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.flightmonitor.core.alert.control.AlertDecision;
import com.flightmonitor.core.alert.control.AlertService;
import com.flightmonitor.core.alert.control.NotificationService;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;

/**
 * Um ciclo de varredura: reivindica os monitores vencidos e os processa.
 *
 * <h2>Reivindicar ANTES de trabalhar</h2>
 *
 * O ciclo agenda a proxima busca <b>no momento em que pega</b> o monitor, e nao
 * depois de terminar. A ordem inversa pareceria mais natural, mas tem dois
 * defeitos graves:
 *
 * <ul>
 *   <li>se a varredura falhar, o monitor continua vencido e volta a ser
 *       escolhido no ciclo seguinte, e no seguinte — <b>laco apertado</b>
 *       martelando uma fonte que ja esta com problema;</li>
 *   <li>se o processo morrer no meio, o monitor fica vencido para sempre.</li>
 * </ul>
 *
 * Reivindicando primeiro, o pior caso vira "esta varredura foi perdida, a
 * proxima acontece no intervalo normal" — um atraso, nao uma avalanche.
 *
 * <h2>Transacoes curtas</h2>
 *
 * A reivindicacao e uma transacao curta; a varredura, que faz HTTP, roda fora
 * de qualquer transacao. Ver D-034 — transacao aberta durante chamada externa
 * esgota o pool de conexoes.
 */
@Service
public class SearchCycleService {

    private static final Logger log = LoggerFactory.getLogger(SearchCycleService.class);

    private final MonitorRepository monitores;
    private final PriceSearchService busca;
    private final AlertService alertas;
    private final NotificationService notificacao;
    private final TransactionTemplate tx;
    private final SchedulerProperties props;
    private final MetricasDaBusca metricas;

    public SearchCycleService(
            MonitorRepository monitores,
            PriceSearchService busca,
            AlertService alertas,
            NotificationService notificacao,
            TransactionTemplate tx,
            SchedulerProperties props,
            MetricasDaBusca metricas) {
        this.monitores = monitores;
        this.busca = busca;
        this.alertas = alertas;
        this.notificacao = notificacao;
        this.tx = tx;
        this.props = props;
        this.metricas = metricas;
    }

    public CycleResult executarCiclo() {
        List<Long> reivindicados = reivindicar();
        if (reivindicados.isEmpty()) {
            return CycleResult.vazio();
        }

        log.info("ciclo iniciado com {} monitor(es)", reivindicados.size());

        int sucesso = 0;
        int falha = 0;
        int oportunidades = 0;
        int alertados = 0;

        for (Long id : reivindicados) {
            try {
                MonitorRunResult resultado = processarMonitor(id);

                if (resultado.busca().falhou()) {
                    falha++;
                } else {
                    sucesso++;
                    if (resultado.busca().temOportunidade()) {
                        oportunidades++;
                        if (resultado.alerta().alertar()) {
                            alertados++;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // Um monitor problematico nao pode derrubar o ciclo inteiro.
                falha++;
                log.error("monitor {} falhou de forma inesperada", id, e);
                reagendarParaRetentativa(id);
            }
        }

        CycleResult resultado = new CycleResult(
                reivindicados.size(), sucesso, falha, oportunidades, alertados);
        log.info("ciclo concluido: {}", resultado);
        return resultado;
    }

    /**
     * Processa um monitor de ponta a ponta: varre, decide e registra.
     *
     * <p><b>Publico e usado tanto pelo scheduler quanto pelo endpoint manual.</b>
     * Na primeira versao, o endpoint chamava a varredura direto e nao avaliava
     * alerta — os dois caminhos divergiram em silencio, e uma varredura manual
     * encontrava oportunidade sem nunca notificar. Ter um unico ponto de entrada
     * impede que isso volte a acontecer.
     */
    public MonitorRunResult processarMonitor(Long monitorId) {
        // Medido AQUI porque este e o unico ponto de entrada da varredura — a
        // mesma propriedade que impede alerta de ser esquecido impede metrica
        // de ficar cega num caminho alternativo.
        long inicio = System.nanoTime();
        SearchOutcome resultado;
        try {
            resultado = busca.varrer(monitorId);
        } finally {
            marcarBuscaFeita(monitorId);
        }
        // Fora do `finally`: se `varrer` lancar, nao ha resultado para
        // classificar, e inventar um desfecho seria pior que nao ter a medida.
        metricas.registrar(resultado, Duration.ofNanos(System.nanoTime() - inicio));

        if (resultado.falhou()) {
            reagendarParaRetentativa(monitorId);
            metricas.registrarDecisao(AlertDecision.Motivo.SEM_OPORTUNIDADE.name(), false);
            return new MonitorRunResult(resultado, AlertDecision.naoAlertar(
                    AlertDecision.Motivo.SEM_OPORTUNIDADE, "a varredura falhou"));
        }

        if (!resultado.temOportunidade()) {
            metricas.registrarDecisao(AlertDecision.Motivo.SEM_OPORTUNIDADE.name(), false);
            return new MonitorRunResult(resultado, AlertDecision.naoAlertar(
                    AlertDecision.Motivo.SEM_OPORTUNIDADE,
                    "nenhuma oferta abaixo do teto que se sustentasse"));
        }

        AlertDecision decisao = alertas.avaliar(resultado);
        metricas.registrarDecisao(decisao.motivo().name(), decisao.alertar());

        if (decisao.alertar()) {
            log.info("monitor {}: ALERTA a {} - {}",
                    monitorId, resultado.melhorPreco(), decisao.detalhe());
            // Entrega imediata. A varredura agendada de pendentes existe como
            // rede de seguranca (queda no meio do caminho, retentativa), nao
            // como caminho normal — esperar ate um minuto para receber um
            // alerta de passagem seria uma espera sem motivo.
            notificacao.despacharPendentes();
        } else {
            log.info("monitor {}: oportunidade a {}, mas sem alerta ({}: {})",
                    monitorId, resultado.melhorPreco(), decisao.motivo(), decisao.detalhe());

            // A camada 2 costuma voltar em minutos. Esperar o intervalo inteiro
            // (6h por padrao) faria perder a oportunidade por causa de uma
            // indisponibilidade passageira. Ver D-041.
            if (decisao.motivo() == AlertDecision.Motivo.SEM_CONFIRMACAO) {
                reagendarParaRetentativa(monitorId);
            }
        }

        return new MonitorRunResult(resultado, decisao);
    }

    /**
     * Pega os monitores vencidos e ja agenda a proxima busca de cada um.
     *
     * <p>Tudo numa transacao curta, com trava de linha e {@code SKIP LOCKED}:
     * outra instancia que rode ao mesmo tempo pega um lote diferente em vez de
     * duplicar o trabalho.
     */
    private List<Long> reivindicar() {
        return tx.execute(status -> {
            Instant agora = Instant.now();
            List<Monitor> devidos = monitores.reivindicarVencidos(
                    agora, PageRequest.of(0, props.batchSize()));

            for (Monitor m : devidos) {
                m.setNextSearchAt(agora.plusSeconds(m.getSearchIntervalMinutes() * 60L));
            }
            monitores.saveAll(devidos);

            return devidos.stream().map(Monitor::getId).toList();
        });
    }

    private void marcarBuscaFeita(Long id) {
        tx.executeWithoutResult(t -> monitores.findById(id)
                .ifPresent(m -> {
                    m.setLastSearchedAt(Instant.now());
                    monitores.save(m);
                }));
    }

    /**
     * Antecipa a proxima tentativa apos uma falha — mas so um pouco.
     *
     * <p>Nunca antes do {@code retryDelay}, para nao martelar uma fonte que ja
     * esta com problema. E nunca DEPOIS do que ja estava agendado: se o
     * intervalo do monitor for menor que o atraso de retentativa, respeitamos o
     * intervalo.
     */
    private void reagendarParaRetentativa(Long id) {
        tx.executeWithoutResult(t -> monitores.findById(id).ifPresent(m -> {
            Instant tentarEm = Instant.now().plus(props.retryDelay());
            if (tentarEm.isBefore(m.getNextSearchAt())) {
                m.setNextSearchAt(tentarEm);
                monitores.save(m);
                log.info("monitor {} sera retentado em {}", id, props.retryDelay());
            }
        }));
    }
}
