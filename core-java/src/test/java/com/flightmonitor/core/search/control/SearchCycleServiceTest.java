package com.flightmonitor.core.search.control;

import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.PriceObservationRepository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
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
import com.flightmonitor.core.search.control.client.dto.WorkerFlightOffer;

/**
 * Testa o ciclo de varredura sem esperar o relogio.
 *
 * <p>O {@link SearchScheduler} so agenda; toda a logica esta no
 * {@link SearchCycleService}. Por isso os testes chamam {@code executarCiclo()}
 * diretamente — nenhum {@code Thread.sleep}, nenhum teste lento e instavel.
 */
@SpringBootTest
class SearchCycleServiceTest {

    @Autowired
    private SearchCycleService ciclo;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private SearchRunRepository execucoes;

    @Autowired
    private SearchClientFalso client;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private SchedulerProperties props;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        SearchClientFalso searchClientFalso() {
            return new SearchClientFalso();
        }
    }

    static class SearchClientFalso implements SearchClient {
        CalendarSearchResult varredura;
        RuntimeException erro;
        int chamadas;

        void limpar() {
            varredura = null;
            erro = null;
            chamadas = 0;
        }

        @Override
        public CalendarSearchResult scanCalendar(CalendarSearchCommand cmd) {
            chamadas++;
            if (erro != null) {
                throw erro;
            }
            return varredura;
        }

        @Override
        public ConfirmResult confirm(ConfirmCommand cmd) {
            return ConfirmResult.degradado("camada 2 fora do ar neste teste");
        }
    }

    @BeforeEach
    @AfterEach
    void limpar() {
        observacoes.deleteAll();
        execucoes.deleteAll();
        monitores.deleteAll();
        if (client != null) {
            client.limpar();
        }
    }

    private Monitor monitor(String label, Instant proximaBusca, boolean ativo) {
        Monitor m = new Monitor();
        m.setLabel(label);
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(LocalDate.now().plusMonths(6));
        m.setDepartureWindowEnd(LocalDate.now().plusMonths(6).plusDays(10));
        m.setMaxPrice(new BigDecimal("3200.00"));
        m.setActive(ativo);
        m.setNextSearchAt(proximaBusca);
        return monitores.saveAndFlush(m);
    }

    private CalendarSearchResult comOferta(String preco) {
        LocalDate ida = LocalDate.now().plusMonths(6).plusDays(1);
        WorkerFlightOffer o = new WorkerFlightOffer(
                ida, ida.plusDays(12), new BigDecimal(preco), "BRL",
                "TAP", "1234", (short) 0, 630, null, null, null, "TRAVELPAYOUTS");
        return new CalendarSearchResult("GRU", "LIS", List.of(o), 1, 1, "SAO", "LIS", List.of());
    }

    // ------------------------------------------------------------ selecao

    @Test
    @DisplayName("o ciclo varre apenas monitores ativos e vencidos")
    void varreApenasVencidosEAtivos() {
        Monitor vencido = monitor("vencido", Instant.now().minus(1, ChronoUnit.HOURS), true);
        monitor("futuro", Instant.now().plus(5, ChronoUnit.HOURS), true);
        monitor("inativo", Instant.now().minus(1, ChronoUnit.HOURS), false);
        client.varredura = comOferta("3500");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.reivindicados()).isEqualTo(1);
        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(client.chamadas).isEqualTo(1);
        assertThat(observacoes.findAll())
                .allSatisfy(o -> assertThat(o.getMonitor().getId()).isEqualTo(vencido.getId()));
    }

    @Test
    @DisplayName("sem monitores vencidos o ciclo fica ocioso e nao chama o worker")
    void cicloOcioso() {
        monitor("futuro", Instant.now().plus(5, ChronoUnit.HOURS), true);

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.ocioso()).isTrue();
        assertThat(client.chamadas).isZero();
    }

    @Test
    @DisplayName("o lote limita quantos monitores um ciclo processa")
    void respeitaOTamanhoDoLote() {
        for (int i = 0; i < props.batchSize() + 3; i++) {
            monitor("m" + i, Instant.now().minus(1, ChronoUnit.HOURS), true);
        }
        client.varredura = comOferta("3500");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.reivindicados()).isEqualTo(props.batchSize());
    }

    // ------------------------------------------------- reivindicar antes

    @Test
    @DisplayName("a proxima busca e agendada ANTES da varredura, nao depois")
    void agendaProximaAoReivindicar() {
        Monitor m = monitor("vencido", Instant.now().minus(1, ChronoUnit.HOURS), true);
        client.varredura = comOferta("3500");
        Instant antes = m.getNextSearchAt();

        ciclo.executarCiclo();

        Monitor recarregado = monitores.findById(m.getId()).orElseThrow();
        assertThat(recarregado.getNextSearchAt()).isAfter(antes);
        assertThat(recarregado.getNextSearchAt()).isAfter(Instant.now());
        assertThat(recarregado.getLastSearchedAt()).isNotNull();
    }

    @Test
    @DisplayName("dois ciclos seguidos nao varrem o mesmo monitor duas vezes")
    void naoRevarreNoCicloSeguinte() {
        monitor("vencido", Instant.now().minus(1, ChronoUnit.HOURS), true);
        client.varredura = comOferta("3500");

        CycleResult primeiro = ciclo.executarCiclo();
        CycleResult segundo = ciclo.executarCiclo();

        assertThat(primeiro.reivindicados()).isEqualTo(1);
        assertThat(segundo.ocioso()).isTrue();
        assertThat(client.chamadas).isEqualTo(1);
    }

    // ----------------------------------------------------------- falhas

    @Test
    @DisplayName("falha na varredura nao deixa o monitor em laco apertado")
    void falhaNaoGeraLacoApertado() {
        Monitor m = monitor("vai falhar", Instant.now().minus(1, ChronoUnit.HOURS), true);
        client.erro = new WorkerUnavailableException("worker fora do ar");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.falha()).isEqualTo(1);
        assertThat(r.sucesso()).isZero();

        Monitor recarregado = monitores.findById(m.getId()).orElseThrow();
        // Foi antecipado para retentativa, mas NAO esta vencido de novo:
        // o ciclo seguinte nao vai pega-lo.
        assertThat(recarregado.getNextSearchAt()).isAfter(Instant.now());

        CycleResult seguinte = ciclo.executarCiclo();
        assertThat(seguinte.ocioso()).isTrue();
    }

    @Test
    @DisplayName("apos falha o monitor volta antes do intervalo normal")
    void falhaAntecipaARetentativa() {
        Monitor m = monitor("vai falhar", Instant.now().minus(1, ChronoUnit.HOURS), true);
        m.setSearchIntervalMinutes(360);
        monitores.saveAndFlush(m);
        client.erro = new WorkerUnavailableException("worker fora do ar");

        ciclo.executarCiclo();

        Monitor recarregado = monitores.findById(m.getId()).orElseThrow();
        Instant intervaloNormal = Instant.now().plus(6, ChronoUnit.HOURS);
        assertThat(recarregado.getNextSearchAt()).isBefore(intervaloNormal);
    }

    @Test
    @DisplayName("um monitor problematico nao derruba os outros do lote")
    void falhaIsoladaNaoDerrubaOCiclo() {
        monitor("primeiro", Instant.now().minus(2, ChronoUnit.HOURS), true);
        monitor("segundo", Instant.now().minus(1, ChronoUnit.HOURS), true);
        // O dublê falha para todos; o que importa e o ciclo processar os dois
        // e contabilizar as duas falhas, em vez de abortar no primeiro erro.
        client.erro = new IllegalStateException("erro inesperado");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.reivindicados()).isEqualTo(2);
        assertThat(r.falha()).isEqualTo(2);
        assertThat(client.chamadas).isEqualTo(2);
    }

    // ------------------------------------------------------ oportunidade

    @Test
    @DisplayName("o ciclo conta as oportunidades encontradas")
    void contaOportunidades() {
        monitor("vencido", Instant.now().minus(1, ChronoUnit.HOURS), true);
        client.varredura = comOferta("2980");  // abaixo do teto de 3200

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.oportunidades()).isEqualTo(1);
    }

    @Test
    @DisplayName("preco acima do teto nao conta como oportunidade")
    void precoAcimaDoTetoNaoEOportunidade() {
        monitor("vencido", Instant.now().minus(1, ChronoUnit.HOURS), true);
        client.varredura = comOferta("3500");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(r.oportunidades()).isZero();
    }

    // -------------------------------------------------- chave de desligamento

    @Test
    @DisplayName("o scheduler esta desligado nos testes, mas o ciclo continua chamavel")
    void schedulerDesligadoNosTestes() {
        // Se o SearchScheduler existisse aqui, ele dispararia durante a suite e
        // chamaria as fontes reais. A ausencia do bean prova que o
        // application.properties de teste foi aplicado.
        assertThat(props.enabled()).isFalse();
        assertThat(contexto.getBeanNamesForType(SearchScheduler.class)).isEmpty();
        assertThat(ciclo).isNotNull();
    }
}
