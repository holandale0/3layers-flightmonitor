package com.flightmonitor.core;

import com.flightmonitor.core.search.control.client.SearchClient;

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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.control.AlertDecision;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.AlertStatus;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.control.MonitorRunResult;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.control.SearchCycleService;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.SearchStatus;

/**
 * E2E entre servicos — etapa E1.16.
 *
 * <h2>A lacuna que so este teste fecha</h2>
 *
 * O {@code MotorE2ETest} (E1.15) cobre o motor inteiro, mas com o worker
 * substituido por WireMock — e o JSON daquele WireMock <b>fui eu que escrevi</b>.
 * Se eu tiver entendido o contrato errado, os dois lados passam: o Java valida a
 * minha suposicao, e o Python valida a mesma suposicao pelo outro lado.
 *
 * <p>Aqui os dois processos sao reais e conversam por HTTP de verdade. Um erro
 * de contrato — nome de campo em snake_case, formato de data, nulo tratado
 * diferente, {@code Decimal} do Python virando {@code BigDecimal} no Java —
 * aparece <b>aqui</b>, e em nenhum outro lugar.
 *
 * <h2>O que continua falso</h2>
 *
 * Somente as fontes externas, via {@code USE_FAKE_PROVIDERS=true} no worker.
 * Nada entre Java e Python e simulado. Ver {@code worker-python/app/providers/fake.py}.
 *
 * <h2>Fora do build padrao, de proposito</h2>
 *
 * Depende de outro processo no ar. Roda pelo script:
 * <pre>
 * python scripts/e2e_servicos.py                  # transporte REST
 * python scripts/e2e_servicos.py --transporte amqp  # transporte AMQP (E4.1)
 * </pre>
 * que sobe o worker falso, executa esta classe e derruba tudo. Sem o
 * {@code -De2e.servicos=true} ela e simplesmente pulada.
 *
 * <p><b>Os mesmos testes rodam nos dois transportes.</b> Nao ha um teste "para
 * AMQP": se o comportamento dependesse do meio, a porta {@code SearchClient}
 * nao estaria cumprindo o papel dela.
 *
 * <h2>Cenarios pelo destino</h2>
 *
 * O core-java nao pode ganhar um "modo de teste" — seria codigo de producao
 * existindo por causa de teste. Mas ele ja manda o destino, entao codigos IATA
 * reservados (faixa {@code ZZ*}, que a IATA nao atribui) selecionam o desfecho
 * do lado do worker. Do lado do core e apenas outra rota.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "e2e.servicos", matches = "true")
class E2EServicosTest {

    /** Precos fixados em {@code worker-python/app/providers/fake.py}. */
    private static final BigDecimal CACHE_BARATO = new BigDecimal("3480.00");
    private static final BigDecimal CACHE_CARO = new BigDecimal("4900.00");
    private static final BigDecimal CONFIRMADO = new BigDecimal("3720.00");
    private static final BigDecimal CONFIRMADO_ABSURDO = new BigDecimal("9990.00");

    private static final BigDecimal TETO = new BigDecimal("4000.00");

    @Autowired
    private SearchCycleService ciclo;

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
    private JdbcTemplate jdbc;

    private Recipient destinatario;

    private final LocalDate ida = LocalDate.now().plusMonths(2);

    @DynamicPropertySource
    static void apontarParaOWorkerFalso(DynamicPropertyRegistry registry) {
        registry.add("flightmonitor.worker.base-url",
                () -> System.getProperty("e2e.worker.url", "http://localhost:8002"));
        // O canal de entrega continua sendo o LOG. O objetivo aqui e o contrato
        // entre Java e Python; a Meta nao entra nesta fronteira.
        registry.add("flightmonitor.notification.canal", () -> "LOG");

        // O TRANSPORTE e parametro desde a E4.1, e a suite inteira roda nos
        // dois. Nenhum teste abaixo sabe qual esta ativo — e esse e exatamente
        // o ponto: se o comportamento dependesse do transporte, a porta
        // SearchClient nao estaria cumprindo o papel dela.
        registry.add("flightmonitor.worker.transporte",
                () -> System.getProperty("e2e.transporte", "REST"));
    }

    @BeforeEach
    void preparar() {
        limparBanco();
        destinatario = destinatarios.saveAndFlush(new Recipient("E2E", "+5511900000000"));
    }

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        alertas.deleteAll();
        observacoes.deleteAll();
        execucoes.deleteAll();
        monitores.deleteAll();
        destinatarios.deleteAll();
    }

    /**
     * @param destino codigo IATA; {@code ZZ*} seleciona um cenario no worker
     */
    private Monitor monitor(String destino) {
        Monitor m = new Monitor();
        m.setLabel("E2E " + destino);
        m.setOrigin("GRU");
        m.setDestination(destino);
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(10));
        m.setMaxPrice(TETO);
        m.setMaxStops((short) 2);
        m.setSearchIntervalMinutes(360);
        m.setNextSearchAt(Instant.now().minus(1, ChronoUnit.HOURS));
        m.addRecipient(destinatario);
        return monitores.saveAndFlush(m);
    }

    // ------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("Java e Python fecham o ciclo inteiro, do monitor ao alerta")
    void doMonitorAoAlerta() {
        Monitor m = monitor("LIS");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        assertThat(r.busca().falhou()).isFalse();
        assertThat(r.busca().temOportunidade()).isTrue();
        assertThat(r.busca().confirmada()).isTrue();
        assertThat(r.busca().melhorPreco()).isEqualByComparingTo(CONFIRMADO);
        assertThat(r.alerta().alertar()).isTrue();

        // 2 da camada 1 + 1 da camada 2. Se a serializacao tivesse perdido uma
        // oferta, o numero mudaria aqui.
        assertThat(observacoes.findAll()).hasSize(3);

        assertThat(alertas.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
            assertThat(a.getChannel()).isEqualTo(AlertChannel.LOG);
            assertThat(a.getProviderMessageId()).startsWith("log:");
            assertThat(a.getMessage())
                    .contains("GRU → LIS")
                    .contains("3.720")
                    // O preco do cache nao pode vazar para a mensagem.
                    .doesNotContain("3.480");
        });
    }

    /**
     * O coracao da E1.16: campo a campo, atravessando a fronteira.
     *
     * <p>Cada assercao aqui corresponde a um jeito diferente de o contrato
     * quebrar em silencio — e nenhum deles seria pego pelos testes de cada lado
     * isoladamente.
     */
    @Test
    @DisplayName("cada campo atravessa a fronteira sem se perder pelo caminho")
    void contratoCampoACampo() {
        Monitor m = monitor("LIS");

        ciclo.processarMonitor(m.getId());

        List<PriceObservation> todas = observacoes.findAll();

        // --- camada 1: snake_case, Decimal e datas
        assertThat(todas)
                .filteredOn(o -> o.getSource() == PriceSource.TRAVELPAYOUTS)
                .hasSize(2)
                .satisfies(cache -> {
                    assertThat(cache).extracting(PriceObservation::getPrice)
                            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                            .containsExactlyInAnyOrder(CACHE_BARATO, CACHE_CARO);
                    assertThat(cache).allSatisfy(o -> {
                        assertThat(o.isConfirmed()).isFalse();
                        assertThat(o.getCurrency()).isEqualTo("BRL");
                        // departure_at e horario LOCAL, sem fuso: se virasse
                        // instante em algum ponto, o horario andaria.
                        assertThat(o.getDepartureAt()).isNotNull();
                        assertThat(o.getDepartureAt().getHour()).isEqualTo(21);
                        assertThat(o.getDepartureAt().getMinute()).isEqualTo(40);
                        // A oferta tem que respeitar a janela pedida (RISCO-007).
                        assertThat(o.getDepartureDate())
                                .isBetween(ida, ida.plusDays(10));
                        assertThat(o.getReturnDate()).isAfter(o.getDepartureDate());
                    });
                });

        // --- camada 2: os campos que so ela tem
        assertThat(todas)
                .filteredOn(PriceObservation::isConfirmed)
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.getSource()).isEqualTo(PriceSource.FAST_FLIGHTS);
                    assertThat(o.getPrice()).isEqualByComparingTo(CONFIRMADO);
                    // airline vem por extenso na camada 2 e como codigo na 1:
                    // trocar um pelo outro passaria despercebido sem isto.
                    assertThat(o.getAirline()).isEqualTo("Iberia");
                    assertThat(o.getStops()).isEqualTo((short) 1);
                    assertThat(o.getDurationMinutes()).isEqualTo(745);
                    // Inteiro do Python chegando como Integer, e nao truncado.
                    assertThat(o.getDepartureAt()).isNotNull();
                    // D-023: a rota gravada e a PEDIDA, mesmo que a fonte
                    // devolva outro codigo.
                    assertThat(o.getOrigin()).isEqualTo("GRU");
                    assertThat(o.getDestination()).isEqualTo("LIS");
                });

        // --- as duas execucoes fecharam
        assertThat(execucoes.findAll())
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(SearchStatus.SUCCESS))
                .extracting(SearchRun::getSource)
                .containsExactlyInAnyOrder(PriceSource.TRAVELPAYOUTS, PriceSource.FAST_FLIGHTS);
    }

    @Test
    @DisplayName("a oferta cara da camada 1 e gravada, mas nao vira candidata")
    void ofertaAcimaDoTetoNaoViraCandidata() {
        Monitor m = monitor("LIS");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        // 4.900 esta acima do teto de 4.000: fica no historico e nao e confirmada.
        assertThat(r.busca().candidatosAbaixoDoTeto()).isEqualTo(1);
        assertThat(observacoes.findAll())
                .anySatisfy(o -> assertThat(o.getPrice()).isEqualByComparingTo(CACHE_CARO));
    }

    // ------------------------------------------------------------- cenarios

    @Test
    @DisplayName("ZZA: o preco real estoura o teto e o alerta nao sai")
    void candidatoIlusorio() {
        Monitor m = monitor("ZZA");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        assertThat(r.busca().falhou()).isFalse();
        assertThat(r.busca().candidatoIlusorio()).isTrue();
        assertThat(r.busca().temOportunidade()).isFalse();
        assertThat(alertas.findAll()).isEmpty();

        // O preco real fica gravado: o historico guarda a verdade, mesmo quando
        // ela desmente o cache.
        assertThat(observacoes.findAll())
                .anySatisfy(o -> assertThat(o.getPrice()).isEqualByComparingTo(CONFIRMADO_ABSURDO));
        assertThat(r.busca().avisos())
                .anySatisfy(a -> assertThat(a).contains("acima do teto"));
    }

    @Test
    @DisplayName("ZZB: camada 2 fora do ar degrada, e o sistema fica em silencio")
    void camada2Degradada() {
        Monitor m = monitor("ZZB");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        assertThat(r.busca().camada2Degradada()).isTrue();
        assertThat(r.busca().temOportunidade()).isTrue();
        assertThat(r.alerta().alertar()).isFalse();
        assertThat(r.alerta().motivo()).isEqualTo(AlertDecision.Motivo.SEM_CONFIRMACAO);
        assertThat(alertas.findAll()).isEmpty();

        // Degradou, nao falhou — e a distincao aparece no registro da execucao.
        assertThat(execucoes.findAll())
                .filteredOn(e -> e.getSource() == PriceSource.FAST_FLIGHTS)
                .singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(SearchStatus.PARTIAL));
    }

    @Test
    @DisplayName("ZZC: 'consultei e nao existe' e diferente de 'nao consegui consultar'")
    void vooNaoExiste() {
        Monitor m = monitor("ZZC");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        // A diferenca que este cenario prova: aqui NAO ha degradacao. A camada 2
        // respondeu; a resposta foi "nao existe voo assim".
        assertThat(r.busca().camada2Degradada()).isFalse();
        assertThat(r.busca().candidatoIlusorio()).isTrue();
        assertThat(alertas.findAll()).isEmpty();
        assertThat(execucoes.findAll())
                .filteredOn(e -> e.getSource() == PriceSource.FAST_FLIGHTS)
                .singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(SearchStatus.SUCCESS));
    }

    @Test
    @DisplayName("ZZD: camada 1 fora do ar vira 502, e a busca falha sem inventar alerta")
    void camada1ForaDoAr() {
        Monitor m = monitor("ZZD");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        assertThat(r.busca().falhou()).isTrue();
        assertThat(observacoes.findAll()).isEmpty();
        assertThat(alertas.findAll()).isEmpty();

        assertThat(execucoes.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(SearchStatus.FAILED);
            // O MOTIVO da fonte precisa sobreviver a viagem — e nao o codigo
            // HTTP, que so existe num dos transportes.
            //
            // A primeira versao exigia "502", e o teste falhou por AMQP com o
            // comportamento CERTO. A assertiva e que estava presa a um detalhe
            // de implementacao do REST; o que importa e que "a fonte caiu"
            // chegue distinguivel de "a janela esta vazia", em qualquer meio.
            assertThat(e.getErrorMessage()).contains("indisponivel");
        });
    }

    @Test
    @DisplayName("ZZE: janela sem oferta nao e o mesmo que fonte morta")
    void janelaVazia() {
        Monitor m = monitor("ZZE");

        MonitorRunResult r = ciclo.processarMonitor(m.getId());

        assertThat(r.busca().falhou()).isFalse();
        assertThat(r.busca().observacoesGravadas()).isZero();
        assertThat(r.busca().temOportunidade()).isFalse();
        assertThat(alertas.findAll()).isEmpty();

        // O aviso e o que permite diagnosticar sem ler log: a fonte respondeu,
        // e o filtro descartou tudo.
        assertThat(r.busca().avisos())
                .anySatisfy(a -> assertThat(a).contains("nenhum dentro da janela"));

        assertThat(execucoes.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(SearchStatus.SUCCESS));
    }

    // ------------------------------------------------------------ anti-spam

    @Test
    @DisplayName("o anti-spam funciona igual com os dois servicos de pe")
    void antiSpamAtravessaOsDoisServicos() {
        Monitor m = monitor("LIS");

        ciclo.processarMonitor(m.getId());
        ciclo.processarMonitor(m.getId());

        assertThat(alertas.findAll()).hasSize(1);

        jdbc.update("update alert set created_at = created_at - make_interval(hours => 24)");

        // Mesmo preco, cooldown vencido: sem queda relevante, continua calado.
        ciclo.processarMonitor(m.getId());
        assertThat(alertas.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("o mesmo monitor varrido duas vezes nao duplica a historia")
    void varredurasSucessivasAcumulamHistorico() {
        Monitor m = monitor("LIS");

        ciclo.processarMonitor(m.getId());
        ciclo.processarMonitor(m.getId());

        // 3 observacoes por varredura. Historico acumula; alerta, nao.
        assertThat(observacoes.findAll()).hasSize(6);
        assertThat(alertas.findAll()).hasSize(1);
    }

    // ------------------------------------------------- prova de que e real

    @Test
    @DisplayName("o worker do outro lado e o falso, e nao a fonte de verdade")
    void estaFalandoComOWorkerFalso() {
        Monitor m = monitor("LIS");

        ciclo.processarMonitor(m.getId());

        // Precos reais de GRU->LIS nunca cairiam exatamente nestes valores. Se
        // esta assercao falhar, o script apontou o teste para o worker de
        // producao — e ai TUDO que ele afirma perde o sentido.
        assertThat(observacoes.findAll())
                .extracting(PriceObservation::getPrice)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(CACHE_BARATO, CACHE_CARO, CONFIRMADO);

        assertThat(alertas.findAll()).extracting(Alert::getStatus)
                .containsExactly(AlertStatus.SENT);
    }
}
