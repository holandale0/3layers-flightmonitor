package com.flightmonitor.core.search.control;

import com.flightmonitor.core.search.entity.SearchStatus;
import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceObservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.monitor.entity.Preferencias;
import com.flightmonitor.core.search.control.client.SearchClient;
import com.flightmonitor.core.search.control.client.WorkerUnavailableException;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmedOffer;
import com.flightmonitor.core.search.control.client.dto.WorkerFlightOffer;

/**
 * Executa uma varredura de precos e grava o historico.
 *
 * <p><b>Nenhum metodo publico aqui e {@code @Transactional}, de proposito.</b> A
 * chamada ao worker pode levar dezenas de segundos — a confirmacao consulta o
 * Google ao vivo. Manter uma transacao aberta durante isso prenderia uma conexao
 * do pool o tempo todo, e com poucos monitores simultaneos o pool acabaria.
 *
 * <p>A persistencia acontece em blocos curtos via {@link TransactionTemplate}.
 * Usar {@code @Transactional} em metodos privados nao funcionaria: o Spring
 * aplica a anotacao por proxy, e chamada interna nao passa pelo proxy.
 */
@Service
public class PriceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PriceSearchService.class);

    private final MonitorRepository monitores;
    private final PriceObservationRepository observacoes;
    private final SearchRunRepository execucoes;
    private final SearchClient client;
    private final TransactionTemplate tx;
    private final SearchProperties props;

    public PriceSearchService(
            MonitorRepository monitores,
            PriceObservationRepository observacoes,
            SearchRunRepository execucoes,
            SearchClient client,
            TransactionTemplate tx,
            SearchProperties props) {
        this.monitores = monitores;
        this.observacoes = observacoes;
        this.execucoes = execucoes;
        this.client = client;
        this.tx = tx;
        this.props = props;
    }

    public SearchOutcome varrer(Long monitorId) {
        // Com as preferencias resolvidas: o filtro de companhias acontece fora
        // de transacao, e uma colecao LAZY estouraria la. Ver BUG-011.
        Monitor monitor = tx.execute(status -> monitores.findByIdComPreferencias(monitorId)
                .orElseThrow(() -> new NotFoundException("Monitor", monitorId)));
        return varrer(monitor);
    }

    public SearchOutcome varrer(Monitor monitor) {
        SearchRun execucao = abrirExecucao(monitor, PriceSource.TRAVELPAYOUTS);

        CalendarSearchResult varredura;
        try {
            varredura = client.scanCalendar(comandoDeVarredura(monitor));
        } catch (WorkerUnavailableException e) {
            registrarFalha(execucao, e.getMessage());
            log.warn("varredura do monitor {} falhou: {}", monitor.getId(), e.getMessage());
            return SearchOutcome.falha(monitor.getId(), e.getMessage());
        }

        List<String> avisos = new ArrayList<>(varredura.warnings());

        List<PriceObservation> gravadas = persistirVarredura(monitor, execucao, varredura);
        concluirExecucao(execucao, SearchStatus.SUCCESS, gravadas.size());

        if (gravadas.isEmpty()) {
            gravadas = consultarReserva(monitor, avisos);
        }

        List<PriceObservation> candidatos = candidatos(monitor, gravadas);
        if (candidatos.isEmpty()) {
            if (varredura.vazioAposFiltro()) {
                avisos.add("a fonte devolveu precos, mas nenhum dentro da janela do monitor");
            }
            return SearchOutcome.semOportunidade(monitor.getId(), gravadas.size(), avisos);
        }

        log.info("monitor {}: {} observacoes, {} abaixo do teto de {}",
                monitor.getId(), gravadas.size(), candidatos.size(), monitor.getMaxPrice());

        PriceObservation melhor = candidatos.get(0);
        if (melhor.isConfirmed()) {
            // Veio da reserva: o preco ja e da camada 2. Confirmar de novo seria
            // perguntar ao Google a mesma coisa duas vezes no mesmo segundo.
            return new SearchOutcome(
                    monitor.getId(), gravadas.size(), candidatos.size(),
                    melhor.getId(), melhor.getPrice(),
                    true, false, false, false, avisos);
        }

        return confirmarMelhorCandidato(monitor, candidatos, gravadas.size(), avisos);
    }

    // ------------------------------------------------- camada 2 como reserva

    /**
     * Consulta a camada 2 dia a dia quando a camada 1 nao trouxe nada (D-114).
     *
     * <p>A regra das duas camadas supunha que a camada 1 sempre enxerga o
     * mercado e so erra o preco. Nao e verdade: o cache dela so tem as datas que
     * alguem pesquisou. Sem esta reserva, uma janela fora do cache nunca gera
     * historico nem alerta — mesmo com o preco caindo abaixo do teto.
     *
     * <p>Os limites existem porque cada dia e uma consulta ao vivo ao Google:
     * so ida (ida e volta multiplicaria as combinacoes), janela curta, e no
     * maximo uma rodada a cada {@code intervalo} por monitor.
     */
    private List<PriceObservation> consultarReserva(Monitor monitor, List<String> avisos) {
        SearchProperties.Reserva reserva = props.reserva();
        if (!reserva.enabled()) {
            return List.of();
        }

        if (monitor.getReturnWindowStart() != null || monitor.getMinStayDays() != null) {
            avisos.add("a camada 1 nao tem preco na janela, e a consulta direta a camada 2"
                    + " so vale para monitor de so ida");
            return List.of();
        }

        LocalDate hoje = LocalDate.now();
        LocalDate inicio = monitor.getDepartureWindowStart().isBefore(hoje)
                ? hoje : monitor.getDepartureWindowStart();
        LocalDate fim = monitor.getDepartureWindowEnd();
        if (fim.isBefore(inicio)) {
            return List.of();
        }

        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > reserva.maxDias()) {
            avisos.add(("a camada 1 nao tem preco na janela, e ela e longa demais (%d dias)"
                    + " para consultar a camada 2 dia a dia: o limite e %d")
                    .formatted(dias, reserva.maxDias()));
            return List.of();
        }

        boolean consultadaHaPouco = execucoes.existsByMonitorIdAndSourceAndStartedAtAfter(
                monitor.getId(), PriceSource.FAST_FLIGHTS, Instant.now().minus(reserva.intervalo()));
        if (consultadaHaPouco) {
            avisos.add("a camada 1 nao tem preco na janela; a camada 2 ja foi consultada"
                    + " ha menos de " + reserva.intervalo().toHours() + "h");
            return List.of();
        }

        List<PriceObservation> gravadas = new ArrayList<>();
        int consultados = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            SearchRun execucao = abrirExecucao(monitor, PriceSource.FAST_FLIGHTS);
            ConfirmResult resultado = client.confirm(comandoDaReserva(monitor, dia));

            if (resultado.degraded()) {
                // Fonte fora do ar: insistir nos dias seguintes so somaria
                // falhas — e pedidos a quem talvez ja esteja nos bloqueando.
                concluirExecucao(execucao, SearchStatus.PARTIAL, 0);
                avisos.addAll(resultado.warnings());
                avisos.add("a camada 2 nao respondeu na consulta direta de " + dia);
                break;
            }

            consultados++;
            if (resultado.naoExiste()) {
                concluirExecucao(execucao, SearchStatus.SUCCESS, 0);
            } else {
                gravadas.add(persistirConfirmacao(monitor, execucao, null, resultado.offer()));
                concluirExecucao(execucao, SearchStatus.SUCCESS, 1);
            }
        }

        avisos.add("a camada 1 nao tem preco na janela; a camada 2 consultou %d dia(s) direto e achou %d preco(s)"
                .formatted(consultados, gravadas.size()));
        log.info("monitor {}: reserva da camada 2 gravou {} preco(s) em {} dia(s)",
                monitor.getId(), gravadas.size(), consultados);
        return gravadas;
    }

    /**
     * Ofertas que valem confirmacao: dentro do teto e dentro das preferencias.
     *
     * <p><b>Filtra o candidato, nao o historico.</b> As observacoes de uma
     * companhia evitada continuam gravadas — o historico pertence a rota (D-016)
     * e serve a estatistica, que descreve o mercado e nao o gosto de quem
     * monitora. O que a preferencia muda e sobre o que o sistema avisa.
     */
    private List<PriceObservation> candidatos(Monitor monitor, List<PriceObservation> gravadas) {
        Set<String> evitadas = companhiasEvitadas(monitor);

        return abaixoDoTeto(gravadas, monitor.getMaxPrice()).stream()
                .filter(o -> !Preferencias.companhiaEvitada(o.getAirline(), evitadas))
                .toList();
    }

    /**
     * Le as companhias evitadas sem confiar em sessao aberta.
     *
     * <p>O caminho normal traz a colecao resolvida por
     * {@code findByIdComPreferencias}. Este guarda cobre quem chamar
     * {@code varrer(Monitor)} com uma entidade desanexada — um teste, ou um
     * caminho novo que alguem escreva daqui a seis meses sem lembrar desta
     * restricao. Falhar a varredura inteira por causa do filtro de preferencia
     * seria trocar uma oportunidade real por uma conveniencia.
     */
    private Set<String> companhiasEvitadas(Monitor monitor) {
        try {
            return Set.copyOf(monitor.getAvoidedAirlines());
        } catch (RuntimeException e) {
            log.warn("preferencias do monitor {} indisponiveis nesta varredura: {}",
                    monitor.getId(), e.getClass().getSimpleName());
            return Set.of();
        }
    }

    // ------------------------------------------------------------- camada 2

    private SearchOutcome confirmarMelhorCandidato(
            Monitor monitor,
            List<PriceObservation> candidatos,
            int gravadas,
            List<String> avisos) {

        List<PriceObservation> aConfirmar = candidatos.stream()
                .limit(props.maxConfirmacoes())
                .toList();

        for (PriceObservation candidato : aConfirmar) {
            SearchRun execucao = abrirExecucao(monitor, PriceSource.FAST_FLIGHTS);
            ConfirmResult resultado = client.confirm(comandoDeConfirmacao(monitor, candidato));
            avisos.addAll(resultado.warnings());

            if (resultado.degraded()) {
                concluirExecucao(execucao, SearchStatus.PARTIAL, 0);
                // Nao sabemos se o preco e real. Seguimos com o candidato da
                // camada 1, sinalizando a incerteza para a regra de alerta.
                return new SearchOutcome(
                        monitor.getId(), gravadas, candidatos.size(),
                        candidato.getId(), candidato.getPrice(),
                        false, true, false, false, avisos);
            }

            if (resultado.naoExiste()) {
                concluirExecucao(execucao, SearchStatus.SUCCESS, 0);
                log.info("monitor {}: candidato de {} em {} nao se sustentou",
                        monitor.getId(), candidato.getPrice(), candidato.getDepartureDate());
                continue;
            }

            PriceObservation confirmada = persistirConfirmacao(
                    monitor, execucao, candidato.getReturnDate(), resultado.offer());
            concluirExecucao(execucao, SearchStatus.SUCCESS, 1);

            boolean aindaCabe = confirmada.getPrice().compareTo(monitor.getMaxPrice()) <= 0;
            if (!aindaCabe) {
                // O preco real estourou o teto: o candidato era um falso-positivo
                // do cache da camada 1. O historico guarda a verdade.
                avisos.add("o preco real (%s) ficou acima do teto (%s)"
                        .formatted(confirmada.getPrice(), monitor.getMaxPrice()));
                continue;
            }

            return new SearchOutcome(
                    monitor.getId(), gravadas + 1, candidatos.size(),
                    confirmada.getId(), confirmada.getPrice(),
                    true, false, false, false, avisos);
        }

        // Todos os candidatos avaliados caíram: nenhum se sustentou.
        return new SearchOutcome(
                monitor.getId(), gravadas, candidatos.size(),
                null, null, false, false, true, false, avisos);
    }

    // ------------------------------------------------------- persistencia

    private List<PriceObservation> persistirVarredura(
            Monitor monitor, SearchRun execucao, CalendarSearchResult varredura) {

        return tx.execute(status -> {
            List<PriceObservation> novas = varredura.offers().stream()
                    .map(oferta -> mapear(monitor, execucao, oferta))
                    .toList();
            return observacoes.saveAll(novas);
        });
    }

    private PriceObservation persistirConfirmacao(
            Monitor monitor, SearchRun execucao, LocalDate voltaPedida, ConfirmedOffer oferta) {

        return tx.execute(status -> {
            PriceObservation o = new PriceObservation(
                    monitor,
                    // A rota gravada e sempre a PEDIDA, nunca a devolvida pela
                    // fonte. Ver D-023 — o historico da rota nao pode se partir
                    // em dois so porque a fonte normaliza aeroporto em cidade.
                    monitor.getOrigin(),
                    monitor.getDestination(),
                    oferta.departureDate(),
                    oferta.price(),
                    PriceSource.FAST_FLIGHTS);
            o.setReturnDate(oferta.returnDate() != null ? oferta.returnDate() : voltaPedida);
            o.setCurrency(oferta.currency() != null ? oferta.currency() : monitor.getCurrency());
            o.setAirline(oferta.airline());
            o.setStops(oferta.stops());
            o.setDurationMinutes(oferta.durationMinutes());
            o.setDepartureAt(oferta.departureAt());
            o.setArrivalAt(oferta.arrivalAt());
            o.setConfirmed(true);
            o.setSearchRun(execucao);
            return observacoes.saveAndFlush(o);
        });
    }

    private PriceObservation mapear(Monitor monitor, SearchRun execucao, WorkerFlightOffer oferta) {
        PriceObservation o = new PriceObservation(
                monitor,
                monitor.getOrigin(),
                monitor.getDestination(),
                oferta.departureDate(),
                oferta.price(),
                PriceSource.TRAVELPAYOUTS);
        o.setReturnDate(oferta.returnDate());
        o.setCurrency(oferta.currency() != null ? oferta.currency() : monitor.getCurrency());
        o.setAirline(oferta.airline());
        o.setAgency(oferta.agency());
        o.setStops(oferta.stops());
        o.setDurationMinutes(oferta.durationMinutes());
        o.setDepartureAt(oferta.departureAt());
        o.setArrivalAt(oferta.arrivalAt());
        // Preco de cidade, vindo de cache: nao e confirmado por definicao.
        o.setConfirmed(false);
        o.setSearchRun(execucao);
        return o;
    }

    // ------------------------------------------------------ execucao (log)

    private SearchRun abrirExecucao(Monitor monitor, PriceSource fonte) {
        return tx.execute(status -> execucoes.saveAndFlush(new SearchRun(monitor, fonte)));
    }

    private void concluirExecucao(SearchRun execucao, SearchStatus status, int observacoes) {
        tx.executeWithoutResult(t -> {
            execucao.concluir(status, observacoes);
            execucoes.saveAndFlush(execucao);
        });
    }

    private void registrarFalha(SearchRun execucao, String motivo) {
        tx.executeWithoutResult(t -> {
            execucao.falhar(motivo);
            execucoes.saveAndFlush(execucao);
        });
    }

    // ------------------------------------------------------------ auxiliares

    private CalendarSearchCommand comandoDeVarredura(Monitor m) {
        return new CalendarSearchCommand(
                m.getOrigin(),
                m.getDestination(),
                m.getDepartureWindowStart(),
                m.getDepartureWindowEnd(),
                m.getReturnWindowStart(),
                m.getReturnWindowEnd(),
                m.getCurrency(),
                m.getMaxStops());
    }

    private ConfirmCommand comandoDeConfirmacao(Monitor m, PriceObservation candidato) {
        return new ConfirmCommand(
                m.getOrigin(),
                m.getDestination(),
                candidato.getDepartureDate(),
                candidato.getReturnDate(),
                m.getCurrency(),
                m.getMaxStops(),
                m.getPassengers(),
                candidato.getPrice());
    }

    /** Consulta de um dia, sem candidato: nao ha preco da camada 1 para comparar. */
    private ConfirmCommand comandoDaReserva(Monitor m, LocalDate dia) {
        return new ConfirmCommand(
                m.getOrigin(),
                m.getDestination(),
                dia,
                null,
                m.getCurrency(),
                m.getMaxStops(),
                m.getPassengers(),
                null);
    }

    /** Candidatos ordenados do mais barato para o mais caro. */
    private List<PriceObservation> abaixoDoTeto(List<PriceObservation> gravadas, BigDecimal teto) {
        return gravadas.stream()
                .filter(o -> o.getPrice().compareTo(teto) <= 0)
                .sorted(Comparator.comparing(PriceObservation::getPrice))
                .toList();
    }

    /** Marca no monitor que a varredura ocorreu e agenda a proxima (etapa E1.9). */
    public void registrarVarredura(Monitor monitor) {
        tx.executeWithoutResult(t -> {
            Monitor gerenciado = monitores.findById(monitor.getId()).orElseThrow();
            gerenciado.registrarBusca(Instant.now());
            monitores.saveAndFlush(gerenciado);
        });
    }
}
