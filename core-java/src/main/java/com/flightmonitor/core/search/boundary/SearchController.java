package com.flightmonitor.core.search.boundary;

import com.flightmonitor.core.search.control.ObservationResponse;
import com.flightmonitor.core.search.control.SearchRunResponse;
import com.flightmonitor.core.search.entity.SearchRunRepository;

import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.control.SearchCycleService;
import com.flightmonitor.core.search.control.MonitorRunResult;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.monitor.entity.MonitorRepository;

/**
 * Varredura sob demanda e leitura do historico.
 *
 * <p>O disparo manual nao e so conveniencia de teste: e o mesmo caminho que o
 * scheduler vai usar na etapa E1.9, entao exercita-lo agora valida o fluxo antes
 * de existir agendamento.
 */
@RestController
@RequestMapping("/api/monitors/{id}")
public class SearchController {

    private final SearchCycleService ciclo;
    private final PriceObservationRepository observacoes;
    private final MonitorRepository monitores;
    private final SearchRunRepository execucoes;

    public SearchController(
            SearchCycleService ciclo,
            PriceObservationRepository observacoes,
            MonitorRepository monitores,
            SearchRunRepository execucoes) {
        this.ciclo = ciclo;
        this.observacoes = observacoes;
        this.monitores = monitores;
        this.execucoes = execucoes;
    }

    /**
     * Dispara a varredura do monitor, com avaliacao de alerta.
     *
     * <p>Delega para o mesmo {@code processarMonitor} que o scheduler usa. Chamar
     * a busca direto daqui faria a varredura manual encontrar oportunidade sem
     * nunca notificar — foi o que aconteceu na primeira versao desta etapa.
     */
    @PostMapping("/search")
    public MonitorRunResult varrer(@PathVariable Long id) {
        if (!monitores.existsById(id)) {
            throw new NotFoundException("Monitor", id);
        }
        return ciclo.processarMonitor(id);
    }

    @GetMapping("/observations")
    public List<ObservationResponse> historico(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {

        if (!monitores.existsById(id)) {
            throw new NotFoundException("Monitor", id);
        }

        return observacoes
                .findByMonitorIdOrderByObservedAtDescIdDesc(id, PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(ObservationResponse::de)
                .toList();
    }

    /** As varreduras mais recentes, das duas camadas, da mais nova para a mais antiga. */
    @GetMapping("/runs")
    public List<SearchRunResponse> varreduras(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {

        if (!monitores.existsById(id)) {
            throw new NotFoundException("Monitor", id);
        }

        return execucoes
                .findByMonitorIdOrderByStartedAtDescIdDesc(id, PageRequest.of(0, Math.clamp(limit, 1, 500)))
                .stream()
                .map(SearchRunResponse::de)
                .toList();
    }
}
