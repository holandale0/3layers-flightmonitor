package com.flightmonitor.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 reorganizou os pacotes de teste para
// org.springframework.boot.<modulo>.test.autoconfigure. Ver D-013.
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.AlertStatus;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.SearchStatus;

import jakarta.persistence.EntityManager;

/**
 * Testes de mapeamento e repositorio contra o PostgreSQL real.
 *
 * <p>Nao usamos banco em memoria de proposito: o schema depende de recursos do
 * PostgreSQL (CHECK com regex, trigger, ON DELETE SET NULL, IDENTITY) que um H2
 * nao reproduz. Testar contra outro banco daria uma falsa sensacao de seguranca.
 *
 * <p>{@code @DataJpaTest} e transacional e faz rollback ao final, entao o banco
 * de desenvolvimento nao acumula lixo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenciaTest {

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private SearchRunRepository execucoes;

    @Autowired
    private AlertRepository alertas;

    @Autowired
    private EntityManager em;

    private Monitor novoMonitor() {
        Monitor m = new Monitor();
        m.setLabel("Lisboa 2027");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(LocalDate.of(2027, 3, 10));
        m.setDepartureWindowEnd(LocalDate.of(2027, 3, 20));
        m.setMinStayDays((short) 10);
        m.setMaxStayDays((short) 15);
        m.setMaxPrice(new BigDecimal("3200.00"));
        m.setMaxStops((short) 1);
        return m;
    }

    @Test
    @DisplayName("monitor persiste e o banco preenche os campos gerados")
    void monitorPersisteComCamposGerados() {
        Monitor salvo = monitores.saveAndFlush(novoMonitor());
        em.refresh(salvo);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getCreatedAt()).isNotNull();
        assertThat(salvo.getUpdatedAt()).isNotNull();
        // Defaults declarados em Java devem bater com os defaults do banco.
        assertThat(salvo.getCurrency()).isEqualTo("BRL");
        assertThat(salvo.getPassengers()).isEqualTo((short) 1);
        assertThat(salvo.isActive()).isTrue();
        assertThat(salvo.getSearchIntervalMinutes()).isEqualTo(360);
    }

    @Test
    @DisplayName("CHECK do banco rejeita IATA em minusculas")
    void checkRejeitaIataInvalido() {
        Monitor m = novoMonitor();
        m.setOrigin("gru");

        assertThatThrownBy(() -> monitores.saveAndFlush(m))
                .hasMessageContaining("monitor_origin_iata");
    }

    @Test
    @DisplayName("CHECK do banco rejeita origem igual ao destino")
    void checkRejeitaRotaDegenerada() {
        Monitor m = novoMonitor();
        m.setDestination("GRU");

        assertThatThrownBy(() -> monitores.saveAndFlush(m))
                .hasMessageContaining("monitor_rota_distinta");
    }

    @Test
    @DisplayName("telefone fora do padrao E.164 e rejeitado")
    void checkRejeitaTelefoneInvalido() {
        Recipient r = new Recipient("Sem mais", "11999998888");

        assertThatThrownBy(() -> destinatarios.saveAndFlush(r))
                .hasMessageContaining("recipient_telefone_e164");
    }

    @Test
    @DisplayName("vinculo N:N entre monitor e destinatario funciona nos dois sentidos")
    void vinculoManyToMany() {
        Recipient r = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999998888"));
        Monitor m = novoMonitor();
        m.addRecipient(r);
        Monitor salvo = monitores.saveAndFlush(m);

        em.clear();

        Monitor recarregado = monitores.findByIdComDestinatarios(salvo.getId()).orElseThrow();
        assertThat(recarregado.getRecipients())
                .hasSize(1)
                .allMatch(d -> d.getPhoneE164().equals("+5511999998888"));
    }

    @Test
    @DisplayName("consulta do scheduler devolve apenas monitores ativos e vencidos")
    void consultaDoScheduler() {
        Monitor vencido = novoMonitor();
        vencido.setNextSearchAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Monitor futuro = novoMonitor();
        futuro.setLabel("ainda nao venceu");
        futuro.setNextSearchAt(Instant.now().plus(5, ChronoUnit.HOURS));

        Monitor inativo = novoMonitor();
        inativo.setLabel("desligado");
        inativo.setActive(false);
        inativo.setNextSearchAt(Instant.now().minus(1, ChronoUnit.HOURS));

        monitores.saveAllAndFlush(List.of(vencido, futuro, inativo));

        // Filtra pelos ids criados aqui: o teste nao pode depender de o banco
        // estar vazio, porque outras suites comitam dados de verdade.
        List<Long> meusIds = List.of(vencido.getId(), futuro.getId(), inativo.getId());
        List<Monitor> devidos = monitores
                .findByActiveTrueAndNextSearchAtLessThanEqualOrderByNextSearchAtAsc(Instant.now())
                .stream()
                .filter(m -> meusIds.contains(m.getId()))
                .toList();

        assertThat(devidos).extracting(Monitor::getLabel).containsExactly("Lisboa 2027");
    }

    @Test
    @DisplayName("registrarBusca agenda a proxima varredura conforme o intervalo")
    void registrarBuscaAgendaProxima() {
        Monitor m = novoMonitor();
        m.setSearchIntervalMinutes(120);
        Instant agora = Instant.now();

        m.registrarBusca(agora);

        assertThat(m.getLastSearchedAt()).isEqualTo(agora);
        assertThat(m.getNextSearchAt()).isEqualTo(agora.plus(2, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("observacao de preco persiste com enum como texto")
    void observacaoPersiste() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        SearchRun run = execucoes.saveAndFlush(new SearchRun(m, PriceSource.TRAVELPAYOUTS));

        PriceObservation o = new PriceObservation(
                m, "GRU", "LIS", LocalDate.of(2027, 3, 12),
                new BigDecimal("2980.00"), PriceSource.TRAVELPAYOUTS);
        o.setReturnDate(LocalDate.of(2027, 3, 27));
        o.setAirline("LATAM");
        o.setStops((short) 0);
        o.setSearchRun(run);

        PriceObservation salva = observacoes.saveAndFlush(o);
        em.refresh(salva);

        assertThat(salva.getObservedAt()).isNotNull();
        assertThat(salva.getSource()).isEqualTo(PriceSource.TRAVELPAYOUTS);

        // O enum precisa estar gravado como texto: e o que o CHECK do banco valida.
        String gravado = (String) em
                .createNativeQuery("select source from price_observation where id = :id")
                .setParameter("id", salva.getId())
                .getSingleResult();
        assertThat(gravado).isEqualTo("TRAVELPAYOUTS");
    }

    @Test
    @DisplayName("estatisticas da rota consideram observacoes de monitores diferentes")
    void estatisticasSaoPorRotaNaoPorMonitor() {
        // Rota exclusiva deste teste: a estatistica e por ROTA e some tudo que
        // existir nela, entao usar GRU->LIS captaria dados de outras suites.
        Monitor um = monitores.saveAndFlush(novoMonitor());
        Monitor outro = novoMonitor();
        outro.setLabel("Outro monitor, mesma rota");
        outro.setMaxPrice(new BigDecimal("4000.00"));
        Monitor dois = monitores.saveAndFlush(outro);

        observacoes.saveAndFlush(new PriceObservation(um, "CGH", "OPO",
                LocalDate.of(2027, 3, 12), new BigDecimal("3000.00"), PriceSource.TRAVELPAYOUTS));
        observacoes.saveAndFlush(new PriceObservation(dois, "CGH", "OPO",
                LocalDate.of(2027, 3, 13), new BigDecimal("2000.00"), PriceSource.TRAVELPAYOUTS));

        Instant desde = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThat(observacoes.menorPrecoDaRota("CGH", "OPO", desde))
                .get()
                .satisfies(v -> assertThat(v).isEqualByComparingTo("2000.00"));
        assertThat(observacoes.precoMedioDaRota("CGH", "OPO", desde))
                .get()
                .satisfies(v -> assertThat(v).isEqualTo(2500.0));
    }

    @Test
    @DisplayName("ultimo preco visto para uma data alimenta o anti-spam")
    void ultimoPrecoDaData() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        LocalDate ida = LocalDate.of(2027, 3, 12);
        LocalDate volta = LocalDate.of(2027, 3, 27);

        PriceObservation antiga = new PriceObservation(m, "GRU", "LIS", ida,
                new BigDecimal("3100.00"), PriceSource.TRAVELPAYOUTS);
        antiga.setReturnDate(volta);
        observacoes.saveAndFlush(antiga);

        PriceObservation recente = new PriceObservation(m, "GRU", "LIS", ida,
                new BigDecimal("2900.00"), PriceSource.FAST_FLIGHTS);
        recente.setReturnDate(volta);
        observacoes.saveAndFlush(recente);

        assertThat(observacoes
                .findFirstByMonitorIdAndDepartureDateAndReturnDateOrderByObservedAtDescIdDesc(m.getId(), ida, volta))
                .get()
                .satisfies(o -> assertThat(o.getPrice()).isEqualByComparingTo("2900.00"));
    }

    @Test
    @DisplayName("apagar o monitor preserva o historico de precos da rota")
    void historicoSobreviveAoMonitor() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        // Rota exclusiva deste teste, para a asserção nao contar observacoes
        // deixadas por outras suites.
        PriceObservation minha = observacoes.saveAndFlush(new PriceObservation(m, "BEL", "MAO",
                LocalDate.of(2027, 3, 12), new BigDecimal("2980.00"), PriceSource.TRAVELPAYOUTS));
        Long idDaMinha = minha.getId();

        // Limpar o contexto antes de apagar e essencial: o ON DELETE SET NULL
        // acontece no banco, e o Hibernate nao sabe disso. Com a observacao ainda
        // gerenciada, ele acusaria referencia a instancia transiente.
        em.flush();
        em.clear();

        monitores.deleteById(m.getId());
        monitores.flush();
        em.clear();

        PriceObservation sobrevivente = observacoes.findById(idDaMinha).orElseThrow();
        assertThat(sobrevivente.getMonitor()).isNull();
        assertThat(sobrevivente.getOrigin()).isEqualTo("BEL");
    }

    @Test
    @DisplayName("ciclo de vida de uma execucao de varredura")
    void cicloDeVidaDaExecucao() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        SearchRun run = execucoes.saveAndFlush(new SearchRun(m, PriceSource.TRAVELPAYOUTS));
        em.refresh(run);

        assertThat(run.getStatus()).isEqualTo(SearchStatus.RUNNING);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNull();

        run.concluir(SearchStatus.SUCCESS, 30);
        execucoes.saveAndFlush(run);

        assertThat(run.getStatus()).isEqualTo(SearchStatus.SUCCESS);
        assertThat(run.getObservationsCount()).isEqualTo(30);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("alerta nasce pendente e registra o id devolvido pelo provedor")
    void cicloDeVidaDoAlerta() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        Recipient r = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999998888"));
        PriceObservation o = observacoes.saveAndFlush(new PriceObservation(m, "GRU", "LIS",
                LocalDate.of(2027, 3, 12), new BigDecimal("2980.00"), PriceSource.TRAVELPAYOUTS));

        Alert a = alertas.saveAndFlush(new Alert(m, o, r, "GRU->LIS por R$ 2.980"));
        em.refresh(a);

        assertThat(a.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(alertas.findByStatus(AlertStatus.PENDING)).contains(a);

        a.marcarEnviado("wamid.ABC123");
        alertas.saveAndFlush(a);

        assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(a.getSentAt()).isNotNull();
        assertThat(alertas.findFirstByMonitorIdOrderByCreatedAtDescIdDesc(m.getId()))
                .get()
                .satisfies(x -> assertThat(x.getProviderMessageId()).isEqualTo("wamid.ABC123"));
    }

    @Test
    @DisplayName("historico do monitor volta do mais recente para o mais antigo")
    void historicoOrdenado() {
        Monitor m = monitores.saveAndFlush(novoMonitor());
        for (int i = 0; i < 3; i++) {
            observacoes.saveAndFlush(new PriceObservation(m, "GRU", "LIS",
                    LocalDate.of(2027, 3, 12 + i), new BigDecimal("3000.00"), PriceSource.TRAVELPAYOUTS));
        }

        List<PriceObservation> pagina = observacoes
                .findByMonitorIdOrderByObservedAtDescIdDesc(m.getId(), PageRequest.of(0, 2));

        assertThat(pagina).hasSize(2);
        assertThat(observacoes.countByMonitorId(m.getId())).isEqualTo(3);
    }
}
