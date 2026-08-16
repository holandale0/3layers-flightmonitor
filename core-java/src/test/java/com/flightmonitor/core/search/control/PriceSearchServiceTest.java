package com.flightmonitor.core.search.control;

import com.flightmonitor.core.search.entity.SearchStatus;
import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceObservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.search.control.client.SearchClient;
import com.flightmonitor.core.search.control.client.WorkerUnavailableException;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmedOffer;
import com.flightmonitor.core.search.control.client.dto.WorkerFlightOffer;

/**
 * Testa a varredura com um {@link SearchClient} falso e o PostgreSQL real.
 *
 * <p>Substituir o cliente e trivial porque a E1.7 o definiu como interface —
 * nao ha WireMock nem servidor HTTP aqui.
 *
 * <p><b>Sem {@code @Transactional} na classe:</b> o servico gerencia suas
 * proprias transacoes via {@code TransactionTemplate}, justamente para nao
 * segurar conexao durante a chamada HTTP. Um teste transacional esconderia o
 * comportamento real de commit. Em troca, cada teste limpa o que criou.
 */
@SpringBootTest
class PriceSearchServiceTest {

    @Autowired
    private PriceSearchService service;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private SearchRunRepository execucoes;

    @Autowired
    private SearchClientFalso client;

    private Monitor monitor;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        SearchClientFalso searchClientFalso() {
            return new SearchClientFalso();
        }
    }

    /** Dublê controlavel: define o que cada camada devolve, ou faz falhar. */
    static class SearchClientFalso implements SearchClient {
        CalendarSearchResult varredura;
        ConfirmResult confirmacao;
        RuntimeException erroNaVarredura;
        final List<ConfirmCommand> confirmacoesPedidas = new ArrayList<>();

        void limpar() {
            varredura = null;
            confirmacao = null;
            erroNaVarredura = null;
            confirmacoesPedidas.clear();
        }

        @Override
        public CalendarSearchResult scanCalendar(CalendarSearchCommand cmd) {
            if (erroNaVarredura != null) {
                throw erroNaVarredura;
            }
            return varredura;
        }

        @Override
        public ConfirmResult confirm(ConfirmCommand cmd) {
            confirmacoesPedidas.add(cmd);
            return confirmacao;
        }
    }

    /**
     * Este teste comita de verdade, entao precisa limpar o que criou.
     *
     * <p>Sem isto, os dados sobram no banco e quebram os testes transacionais
     * que rodam depois — foi exatamente o que aconteceu na primeira execucao da
     * suite completa. A limpeza vai no {@code @AfterEach}, e nao so no
     * {@code @BeforeEach}, para nao deixar residuo mesmo se esta classe rodar
     * por ultimo.
     */
    @org.junit.jupiter.api.AfterEach
    void limparBanco() {
        observacoes.deleteAll();
        execucoes.deleteAll();
        monitores.deleteAll();
    }

    @BeforeEach
    void preparar() {
        observacoes.deleteAll();
        execucoes.deleteAll();
        monitores.deleteAll();
        client.limpar();

        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(LocalDate.now().plusMonths(6));
        m.setDepartureWindowEnd(LocalDate.now().plusMonths(6).plusDays(10));
        m.setMaxPrice(new BigDecimal("3200.00"));
        m.setMaxStops((short) 1);
        monitor = monitores.saveAndFlush(m);
    }

    private WorkerFlightOffer oferta(String preco, int diaDoMes) {
        LocalDate ida = LocalDate.now().plusMonths(6).plusDays(diaDoMes);
        return new WorkerFlightOffer(
                ida, ida.plusDays(12), new BigDecimal(preco), "BRL",
                "TAP", null, "1234", (short) 0, 630,
                LocalDateTime.of(ida, java.time.LocalTime.of(22, 30)),
                null, null, "TRAVELPAYOUTS");
    }

    private CalendarSearchResult varreduraCom(WorkerFlightOffer... ofertas) {
        return new CalendarSearchResult(
                "GRU", "LIS", List.of(ofertas), ofertas.length, ofertas.length,
                "SAO", "LIS", List.of());
    }

    private ConfirmResult confirmado(String preco) {
        ConfirmedOffer o = new ConfirmedOffer(
                LocalDate.now().plusMonths(6).plusDays(1),
                LocalDate.now().plusMonths(6).plusDays(13),
                new BigDecimal(preco), "BRL",
                "Tap Air Portugal", "TP", (short) 0, 590,
                null, LocalDateTime.now().plusMonths(6),
                "GRU", "LIS", "FAST_FLIGHTS");
        return new ConfirmResult(true, false, o, "fast-flights", List.of(), List.of());
    }

    // --------------------------------------------------------- persistencia

    @Test
    @DisplayName("as ofertas da camada 1 viram observacoes no banco")
    void gravaObservacoes() {
        client.varredura = varreduraCom(oferta("3500", 1), oferta("3800", 2));

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.observacoesGravadas()).isEqualTo(2);
        List<PriceObservation> gravadas = observacoes.findAll();
        assertThat(gravadas).hasSize(2);
        assertThat(gravadas).allSatisfy(o -> {
            assertThat(o.getSource()).isEqualTo(PriceSource.TRAVELPAYOUTS);
            assertThat(o.isConfirmed()).isFalse();
            assertThat(o.getObservedAt()).isNotNull();
            assertThat(o.getSearchRun()).isNotNull();
        });
    }

    @Test
    @DisplayName("a rota gravada e a do monitor, nao a devolvida pela fonte")
    void gravaRotaPedida() {
        // A fonte respondeu com provider_origin=SAO; o historico usa GRU (D-023).
        client.varredura = varreduraCom(oferta("3500", 1));

        service.varrer(monitor);

        assertThat(observacoes.findAll()).allSatisfy(o -> {
            assertThat(o.getOrigin()).isEqualTo("GRU");
            assertThat(o.getDestination()).isEqualTo("LIS");
        });
    }

    @Test
    @DisplayName("varredura sem oferta abaixo do teto nao aciona a camada 2")
    void naoConfirmaSemCandidato() {
        client.varredura = varreduraCom(oferta("3500", 1), oferta("3800", 2));

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.candidatosAbaixoDoTeto()).isZero();
        assertThat(r.temOportunidade()).isFalse();
        assertThat(client.confirmacoesPedidas).isEmpty();
    }

    // ------------------------------------------------------------- camada 2

    @Test
    @DisplayName("candidato abaixo do teto e confirmado e gravado")
    void confirmaCandidato() {
        client.varredura = varreduraCom(oferta("3500", 0), oferta("2980", 1));
        client.confirmacao = confirmado("2950");

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.candidatosAbaixoDoTeto()).isEqualTo(1);
        assertThat(r.confirmada()).isTrue();
        assertThat(r.temOportunidade()).isTrue();
        assertThat(r.melhorPreco()).isEqualByComparingTo("2950");

        PriceObservation confirmada = observacoes.findAll().stream()
                .filter(PriceObservation::isConfirmed)
                .findFirst().orElseThrow();
        assertThat(confirmada.getSource()).isEqualTo(PriceSource.FAST_FLIGHTS);
        assertThat(confirmada.getAirline()).isEqualTo("Tap Air Portugal");
        assertThat(confirmada.getDurationMinutes()).isEqualTo(590);
    }

    @Test
    @DisplayName("so o candidato mais barato vai para a camada 2")
    void confirmaApenasOMaisBarato() {
        client.varredura = varreduraCom(oferta("3100", 0), oferta("2980", 1), oferta("3000", 2));
        client.confirmacao = confirmado("2980");

        service.varrer(monitor);

        assertThat(client.confirmacoesPedidas).hasSize(1);
        assertThat(client.confirmacoesPedidas.get(0).candidatePrice())
                .isEqualByComparingTo("2980");
    }

    @Test
    @DisplayName("camada 2 degradada mantem a oportunidade, mas sinaliza a incerteza")
    void camada2Degradada() {
        client.varredura = varreduraCom(oferta("2980", 1));
        client.confirmacao = ConfirmResult.degradado("todas as fontes falharam");

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.temOportunidade()).isTrue();
        assertThat(r.camada2Degradada()).isTrue();
        assertThat(r.confirmada()).isFalse();
        assertThat(r.melhorPreco()).isEqualByComparingTo("2980");
        // A execucao da camada 2 fica como PARTIAL: tentamos e nao deu.
        assertThat(execucoes.findAll()).anySatisfy(e -> {
            assertThat(e.getSource()).isEqualTo(PriceSource.FAST_FLIGHTS);
            assertThat(e.getStatus()).isEqualTo(SearchStatus.PARTIAL);
        });
    }

    @Test
    @DisplayName("candidato ilusorio nao vira oportunidade")
    void candidatoIlusorio() {
        client.varredura = varreduraCom(oferta("2980", 1));
        // confirmed=false e degraded=false: consultamos e o voo nao existe.
        client.confirmacao = new ConfirmResult(
                false, false, null, "fast-flights", List.of(),
                List.of("o candidato da camada 1 nao se sustentou"));

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.candidatoIlusorio()).isTrue();
        assertThat(r.temOportunidade()).isFalse();
        assertThat(r.camada2Degradada()).isFalse();
    }

    @Test
    @DisplayName("preco real acima do teto e gravado, mas nao vira oportunidade")
    void falsoPositivoDoCache() {
        // Cenario real medido na E1.6: cache dizia 3375, real era 5438.
        client.varredura = varreduraCom(oferta("3000", 1));
        client.confirmacao = confirmado("5438");

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.temOportunidade()).isFalse();
        assertThat(r.avisos()).anyMatch(a -> a.contains("acima do teto"));

        // O historico guarda a VERDADE: o preco real foi gravado.
        assertThat(observacoes.findAll())
                .anySatisfy(o -> assertThat(o.getPrice()).isEqualByComparingTo("5438"));
    }

    // ---------------------------------------------------------- execucoes

    @Test
    @DisplayName("execucao bem-sucedida vira SUCCESS com a contagem certa")
    void registraExecucaoDeSucesso() {
        client.varredura = varreduraCom(oferta("3500", 1), oferta("3600", 2));

        service.varrer(monitor);

        SearchRun run = execucoes.findAll().stream()
                .filter(e -> e.getSource() == PriceSource.TRAVELPAYOUTS)
                .findFirst().orElseThrow();
        assertThat(run.getStatus()).isEqualTo(SearchStatus.SUCCESS);
        assertThat(run.getObservationsCount()).isEqualTo(2);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("worker fora do ar marca a execucao como FAILED e nao grava nada")
    void workerForaDoArMarcaFalha() {
        client.erroNaVarredura = new WorkerUnavailableException("conexao recusada");

        SearchOutcome r = service.varrer(monitor);

        assertThat(r.falhou()).isTrue();
        assertThat(r.temOportunidade()).isFalse();
        assertThat(observacoes.findAll()).isEmpty();

        SearchRun run = execucoes.findAll().get(0);
        assertThat(run.getStatus()).isEqualTo(SearchStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("conexao recusada");
    }

    @Test
    @DisplayName("registrarVarredura agenda a proxima conforme o intervalo")
    void agendaProximaVarredura() {
        service.registrarVarredura(monitor);

        Monitor recarregado = monitores.findById(monitor.getId()).orElseThrow();
        assertThat(recarregado.getLastSearchedAt()).isNotNull();
        assertThat(recarregado.getNextSearchAt())
                .isAfter(recarregado.getLastSearchedAt());
    }

    @Test
    @DisplayName("o historico acumula entre varreduras, em vez de sobrescrever")
    void historicoAcumula() {
        client.varredura = varreduraCom(oferta("3500", 1));
        service.varrer(monitor);

        client.varredura = varreduraCom(oferta("3400", 1));
        service.varrer(monitor);

        // Mesma data, precos diferentes: sao duas observacoes, nao uma atualizada.
        assertThat(observacoes.countByMonitorId(monitor.getId())).isEqualTo(2);
    }
}
