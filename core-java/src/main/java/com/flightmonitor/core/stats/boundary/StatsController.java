package com.flightmonitor.core.stats.boundary;

import com.flightmonitor.core.stats.control.RouteStatsService;
import com.flightmonitor.core.stats.control.RouteStats;
import com.flightmonitor.core.stats.control.PriceTrendService;
import com.flightmonitor.core.stats.control.PriceTrend;
import com.flightmonitor.core.stats.control.PriceAnomalyService;
import com.flightmonitor.core.stats.control.PriceAnomaly;
import com.flightmonitor.core.stats.control.FonteDeStats;
import com.flightmonitor.core.stats.control.FlightScoreService;
import com.flightmonitor.core.stats.control.FlightScore;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;

/**
 * Leitura das estatisticas de rota — etapa E2.1.
 *
 * <p>Duas portas para o mesmo calculo. A por rota serve a exploracao ("como esta
 * GRU-LIS?"); a por monitor e a que o painel usa, porque ali o usuario pensa em
 * termos do monitor que cadastrou, e nao de codigos IATA.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final RouteStatsService service;
    private final PriceAnomalyService anomalias;
    private final FlightScoreService score;
    private final PriceTrendService tendencias;
    private final MonitorRepository monitores;
    private final PriceObservationRepository observacoes;

    public StatsController(
            RouteStatsService service,
            PriceAnomalyService anomalias,
            FlightScoreService score,
            PriceTrendService tendencias,
            MonitorRepository monitores,
            PriceObservationRepository observacoes) {
        this.service = service;
        this.anomalias = anomalias;
        this.score = score;
        this.tendencias = tendencias;
        this.monitores = monitores;
        this.observacoes = observacoes;
    }

    /**
     * @param fonte {@code TODAS} (padrao) ou {@code CONFIRMADAS}. Ver
     *        {@link FonteDeStats} — a escolha muda o significado do resultado
     * @param dias janela de observacoes; ausente usa o padrao da configuracao
     */
    @GetMapping("/routes/{origin}/{destination}")
    public RouteStats daRota(
            @PathVariable String origin,
            @PathVariable String destination,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        return service.resumir(origin, destination, fonte, janela(dias));
    }

    @GetMapping("/routes/{origin}/{destination}/months")
    public List<RouteStatsService.MesDaRota> mesesDaRota(
            @PathVariable String origin,
            @PathVariable String destination,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        return service.resumirPorMes(origin, destination, fonte, janela(dias));
    }

    @GetMapping("/monitors/{id}")
    public RouteStats doMonitor(
            @PathVariable Long id,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        Monitor m = monitores.findById(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));

        // Pela ROTA do monitor, e nao pelas observacoes daquele monitor: dois
        // monitores de GRU-LIS compartilham a mesma historia (D-016). Um monitor
        // recem-criado ja nasce com o historico da rota.
        return service.resumir(m.getOrigin(), m.getDestination(), fonte, janela(dias));
    }

    @GetMapping("/monitors/{id}/months")
    public List<RouteStatsService.MesDaRota> mesesDoMonitor(
            @PathVariable Long id,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        Monitor m = monitores.findById(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));

        return service.resumirPorMes(m.getOrigin(), m.getDestination(), fonte, janela(dias));
    }

    /**
     * Avalia um preco contra a historia da rota — etapa E2.2.
     *
     * <p>{@code GET} com um preco no parametro: e uma pergunta, nao um comando.
     * Nada e gravado, e a mesma pergunta feita duas vezes da a mesma resposta.
     *
     * @param fonte julgar um preco confirmado contra estatistica de cache e o
     *        erro descrito na D-060 — passe {@code CONFIRMADAS} para precos
     *        verificados ao vivo
     */
    @GetMapping("/routes/{origin}/{destination}/anomaly")
    public PriceAnomaly anomaliaDaRota(
            @PathVariable String origin,
            @PathVariable String destination,
            @RequestParam BigDecimal preco,
            @RequestParam(required = false) FonteDeStats fonte) {

        return anomalias.avaliar(preco, service.resumir(origin, destination, fonte));
    }

    @GetMapping("/monitors/{id}/anomaly")
    public PriceAnomaly anomaliaDoMonitor(
            @PathVariable Long id,
            @RequestParam BigDecimal preco,
            @RequestParam(required = false) FonteDeStats fonte) {

        Monitor m = monitores.findById(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));

        return anomalias.avaliar(preco, service.resumir(m.getOrigin(), m.getDestination(), fonte));
    }

    /**
     * Nota de 0 a 100 de uma observacao ja gravada — etapa E2.3.
     *
     * <p>Pontua uma observacao EXISTENTE, e nao um voo hipotetico enviado no
     * corpo: a nota nasce de comparacao com o historico da rota, entao ela so
     * faz sentido para algo que o sistema realmente viu.
     */
    @GetMapping("/observations/{id}/score")
    public FlightScore scoreDaObservacao(
            @PathVariable Long id,
            @RequestParam(required = false) FonteDeStats fonte) {

        PriceObservation o = observacoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Observacao", id));

        return score.pontuar(o, fonte);
    }

    /**
     * Para onde o preco da rota vem andando — etapa E2.5.
     *
     * <p>A resposta traz a serie usada, e nao so a conclusao: e dela que sai o
     * grafico do painel, e sem ela um resultado estranho seria impossivel de
     * conferir.
     */
    @GetMapping("/routes/{origin}/{destination}/trend")
    public PriceTrend tendenciaDaRota(
            @PathVariable String origin,
            @PathVariable String destination,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        return tendencias.analisar(origin, destination, fonte, janela(dias));
    }

    @GetMapping("/monitors/{id}/trend")
    public PriceTrend tendenciaDoMonitor(
            @PathVariable Long id,
            @RequestParam(required = false) FonteDeStats fonte,
            @RequestParam(required = false) Integer dias) {

        Monitor m = monitores.findById(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));

        return tendencias.analisar(m.getOrigin(), m.getDestination(), fonte, janela(dias));
    }

    /** {@code null} deixa o servico usar a janela configurada. */
    private Duration janela(Integer dias) {
        if (dias == null) {
            return null;
        }
        // Um dia e o minimo util; valores absurdos viram o limite, em vez de
        // erro: e um parametro de exploracao, nao um comando.
        int limitado = Math.max(1, Math.min(dias, 3650));
        return Duration.ofDays(limitado);
    }
}
