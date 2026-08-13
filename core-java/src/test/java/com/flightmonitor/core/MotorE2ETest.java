package com.flightmonitor.core;

import com.flightmonitor.core.search.control.client.SearchClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.flightmonitor.core.search.control.CycleResult;
import com.flightmonitor.core.search.control.MonitorRunResult;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.control.SearchCycleService;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.SearchRunRepository;
import com.flightmonitor.core.search.entity.SearchStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * E2E do motor — etapa E1.15.
 *
 * <p>O produto inteiro em um teste: <b>monitor cadastrado &rarr; ciclo dispara
 * &rarr; camada 1 &rarr; camada 2 &rarr; observacao gravada &rarr; regra de
 * alerta &rarr; alerta entregue</b>. Tudo real — Spring completo, PostgreSQL
 * real, transacoes reais, o {@code RestSearchClient} de producao falando HTTP de
 * verdade. A unica peca substituida e o <b>worker Python</b>, trocado por
 * WireMock.
 *
 * <h2>Por que este teste existe, se ja ha 129 outros</h2>
 *
 * Os testes anteriores cobrem pecas: o cliente HTTP com o servico ausente, a
 * regra de alerta com a varredura simulada, o ciclo com um {@code SearchClient}
 * falso. Nenhum deles verificava a frase que resume o produto — <i>"monitor
 * cadastrado leva a alerta entregue"</i>. Era a lacuna 2 da secao 9 do
 * PLANO-DE-ACAO.
 *
 * <p>A diferenca nao e teorica. O BUG-005 (endpoint manual que achava
 * oportunidade e nunca alertava) e o BUG-006 ({@code LazyInitializationException}
 * na entrega, que deixava todo alerta preso em PENDING) sao exatamente erros de
 * <b>costura</b>: cada peca passava sozinha. Os dois teriam sido pegos aqui.
 *
 * <h2>Escolhas deliberadas</h2>
 *
 * <ul>
 *   <li><b>WireMock, nao um dublê de {@code SearchClient}.</b> Um dublê pularia
 *       serializacao, snake_case, HTTP/1.1 (BUG-004) e timeout. O JSON abaixo foi
 *       copiado do formato real devolvido pelo worker nas etapas E1.5 e E1.6.</li>
 *   <li><b>Canal LOG fixado por propriedade.</b> O {@code .env} da raiz e lido
 *       pelo {@code application.yml}; se um dia ele trouxer
 *       {@code NOTIFICATION_CHANNEL=WHATSAPP}, esta suite mandaria mensagem de
 *       verdade — e cobrada. Fixar aqui e a trava contra isso.</li>
 *   <li><b>O canal LOG e o dublê de entrega, e le o telefone do destinatario</b>
 *       fora de transacao. E por isso que ele detecta o BUG-006, coisa que um
 *       canal falso que ignora a entidade nao faria.</li>
 *   <li><b>Rota POA&rarr;MAD</b>, usada so aqui: reduz a chance de um teste
 *       vizinho enxergar dados deste.</li>
 * </ul>
 */
@SpringBootTest
class MotorE2ETest {

    private static WireMockServer worker;

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

    private Monitor monitor;
    private Recipient destinatario;

    private final LocalDate ida = LocalDate.now().plusMonths(6);
    private final LocalDate volta = ida.plusDays(12);

    private static final BigDecimal TETO = new BigDecimal("4000.00");

    @BeforeAll
    static void subirWorkerFalso() {
        worker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        worker.start();
    }

    @AfterAll
    static void derrubarWorkerFalso() {
        worker.stop();
    }

    /**
     * Aponta a aplicacao para o WireMock antes do contexto subir.
     *
     * <p>{@code @DynamicPropertySource} e o unico jeito de injetar uma porta
     * sorteada em tempo de execucao: {@code @TestPropertySource} exige valor
     * literal, conhecido em tempo de compilacao.
     */
    @DynamicPropertySource
    static void apontarParaOWorkerFalso(DynamicPropertyRegistry registry) {
        registry.add("flightmonitor.worker.base-url", () -> "http://localhost:" + worker.port());
        // Trava de seguranca: nunca mandar mensagem de verdade a partir da suite.
        registry.add("flightmonitor.notification.canal", () -> "LOG");
    }

    /**
     * Limpeza e preparo no MESMO metodo.
     *
     * <p>Dois {@code @BeforeEach} separados nao tem ordem garantida pelo JUnit —
     * ja quebrou uma suite inteira neste projeto.
     */
    @BeforeEach
    void preparar() {
        limparBanco();
        worker.resetAll();

        destinatario = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        monitor = salvarMonitor(true);
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

    private Monitor salvarMonitor(boolean comDestinatario) {
        Monitor m = new Monitor();
        m.setLabel("Madri no inverno");
        m.setOrigin("POA");
        m.setDestination("MAD");
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(10));
        m.setMaxPrice(TETO);
        m.setMaxStops((short) 1);
        m.setSearchIntervalMinutes(360);
        m.setNextSearchAt(Instant.now().minus(1, ChronoUnit.HOURS));
        if (comDestinatario) {
            m.addRecipient(destinatario);
        }
        return monitores.saveAndFlush(m);
    }

    // ------------------------------------------------------- worker falso

    /** Camada 1: o cache devolve uma oferta abaixo do teto. */
    private void camada1Responde(String preco) {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "origin": "POA",
                          "destination": "MAD",
                          "offers": [{
                            "departure_date": "%s",
                            "return_date": "%s",
                            "price": "%s",
                            "currency": "BRL",
                            "airline": "IB",
                            "flight_number": "6026",
                            "stops": 1,
                            "departure_at": "%sT21:40:00",
                            "arrival_at": "%sT12:05:00",
                            "expires_at": "2026-08-09T20:14:40Z",
                            "source": "TRAVELPAYOUTS"
                          }],
                          "returned": 30,
                          "kept": 1,
                          "provider_origin": "POA",
                          "provider_destination": "MAD",
                          "warnings": []
                        }
                        """.formatted(ida, volta, preco, ida, volta))));
    }

    /** Camada 2: o preco se sustenta ao ser verificado ao vivo. */
    private void camada2Confirma(String preco) {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "confirmed": true,
                          "degraded": false,
                          "offer": {
                            "departure_date": "%s",
                            "return_date": "%s",
                            "price": "%s",
                            "currency": "BRL",
                            "airline": "Iberia",
                            "airline_code": "IB",
                            "stops": 1,
                            "duration_minutes": 745,
                            "departure_at": "%sT21:40:00",
                            "arrival_at": "%sT12:05:00",
                            "departure_airport": "POA",
                            "arrival_airport": "MAD",
                            "source": "FAST_FLIGHTS"
                          },
                          "provider": "fast-flights",
                          "attempts": [
                            {"provider":"fast-flights","ok":true,"found":true,"error":null,"duration_ms":1140}
                          ],
                          "warnings": []
                        }
                        """.formatted(ida, volta, preco, ida, volta))));
    }

    private void camada2Degradada() {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"confirmed": false, "degraded": true, "provider": null,
                         "attempts": [
                           {"provider":"fast-flights","ok":false,"found":false,
                            "error":"protobuf recusado pela fonte","duration_ms":820}
                         ],
                         "warnings": ["nenhum provider de confirmacao respondeu"]}
                        """)));
    }

    // ------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("do monitor cadastrado ao alerta entregue, sem atalho em lugar nenhum")
    void doMonitorAoAlerta() {
        camada1Responde("3480.00");
        // O preco real fica ACIMA do cache e AINDA ASSIM abaixo do teto. E o caso
        // que separa "alertou" de "alertou com o numero certo": o alerta tem que
        // carregar 3720, o preco confirmado, e nunca 3480, o preco do cache.
        camada2Confirma("3720.00");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.reivindicados()).isEqualTo(1);
        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(r.falha()).isZero();
        assertThat(r.oportunidades()).isEqualTo(1);
        assertThat(r.alertados()).isEqualTo(1);

        // --- o historico guarda as duas camadas, nao so a vencedora
        List<PriceObservation> gravadas = observacoes.findAll();
        assertThat(gravadas).hasSize(2);
        assertThat(gravadas).anySatisfy(o -> {
            assertThat(o.getSource()).isEqualTo(PriceSource.TRAVELPAYOUTS);
            assertThat(o.isConfirmed()).isFalse();
            assertThat(o.getPrice()).isEqualByComparingTo("3480.00");
        });
        assertThat(gravadas).anySatisfy(o -> {
            assertThat(o.getSource()).isEqualTo(PriceSource.FAST_FLIGHTS);
            assertThat(o.isConfirmed()).isTrue();
            assertThat(o.getPrice()).isEqualByComparingTo("3720.00");
            assertThat(o.getAirline()).isEqualTo("Iberia");
            assertThat(o.getDurationMinutes()).isEqualTo(745);
            // A rota gravada e a PEDIDA, nunca a devolvida pela fonte (D-023).
            assertThat(o.getOrigin()).isEqualTo("POA");
            assertThat(o.getDestination()).isEqualTo("MAD");
        });

        // --- as duas execucoes ficaram registradas e fechadas
        List<SearchRun> corridas = execucoes.findAll();
        assertThat(corridas).hasSize(2);
        assertThat(corridas).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(SearchStatus.SUCCESS);
            assertThat(e.getFinishedAt()).isNotNull();
        });
        assertThat(corridas).extracting(SearchRun::getSource)
                .containsExactlyInAnyOrder(PriceSource.TRAVELPAYOUTS, PriceSource.FAST_FLIGHTS);

        // --- o alerta saiu, e saiu ENTREGUE
        List<Alert> criados = alertas.findAll();
        assertThat(criados).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
            assertThat(a.getChannel()).isEqualTo(AlertChannel.LOG);
            assertThat(a.getSentAt()).isNotNull();
            assertThat(a.getErrorMessage()).isNull();
            // Sem provider_message_id o alerta seria irrastreavel. E a prova de
            // que o canal rodou de verdade, e nao so mudou o status.
            assertThat(a.getProviderMessageId()).startsWith("log:");
            assertThat(a.getMessage())
                    .contains("POA → MAD")
                    .contains("Iberia")
                    .contains("3.720")
                    .contains("Madri no inverno")
                    // O preco do cache nao pode vazar para a mensagem.
                    .doesNotContain("3.480");
        });

        // --- e aponta para a observacao CONFIRMADA e para o destinatario certo
        Map<String, Object> linha = jdbc.queryForMap("""
                select a.price_observation_id, a.recipient_id, a.monitor_id, o.confirmed
                  from alert a join price_observation o on o.id = a.price_observation_id
                """);
        assertThat(linha.get("confirmed")).isEqualTo(true);
        assertThat(linha.get("recipient_id")).isEqualTo(destinatario.getId());
        assertThat(linha.get("monitor_id")).isEqualTo(monitor.getId());

        // --- o monitor voltou para a fila, no futuro
        Monitor recarregado = monitores.findById(monitor.getId()).orElseThrow();
        assertThat(recarregado.getLastSearchedAt()).isNotNull();
        assertThat(recarregado.getNextSearchAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("o worker recebe a janela do monitor, em snake_case, nas duas camadas")
    void oPedidoQueSaiRespeitaOMonitor() {
        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        ciclo.executarCiclo();

        // Camada 1: a janela vem do monitor, nao de um padrao qualquer.
        worker.verify(postRequestedFor(urlEqualTo("/search/calendar"))
                .withRequestBody(matchingJsonPath("$.origin", equalTo("POA")))
                .withRequestBody(matchingJsonPath("$.destination", equalTo("MAD")))
                .withRequestBody(matchingJsonPath("$.departure_from", equalTo(ida.toString())))
                .withRequestBody(matchingJsonPath("$.departure_to", equalTo(ida.plusDays(10).toString())))
                .withRequestBody(matchingJsonPath("$.max_stops", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.currency", equalTo("BRL"))));

        // Camada 2: as datas vem do CANDIDATO, e o preco dele vai junto para o
        // worker medir a divergencia entre cache e realidade.
        worker.verify(postRequestedFor(urlEqualTo("/search/confirm"))
                .withRequestBody(matchingJsonPath("$.departure_date", equalTo(ida.toString())))
                .withRequestBody(matchingJsonPath("$.return_date", equalTo(volta.toString())))
                .withRequestBody(matchingJsonPath("$.candidate_price")));
    }

    // ------------------------------------------------ oportunidade recusada

    @Test
    @DisplayName("candidato ilusorio do cache nao vira alerta, mas vira historico")
    void precoRealAcimaDoTetoNaoAlerta() {
        camada1Responde("3480.00");
        // Numero real medido na etapa E1.6: o cache anunciava 3.375 e o preco de
        // verdade era 5.714. Sem a camada 2, isto aqui seria um alerta falso.
        camada2Confirma("5714.00");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(r.oportunidades()).isZero();
        assertThat(alertas.findAll()).isEmpty();

        // O preco real fica gravado: o historico guarda a verdade, mesmo quando
        // ela desmente o cache.
        assertThat(observacoes.findAll())
                .anySatisfy(o -> assertThat(o.getPrice()).isEqualByComparingTo("5714.00"));
    }

    @Test
    @DisplayName("camada 2 fora do ar: o sistema fica em silencio em vez de chutar")
    void camada2ForaDoArNaoAlerta() {
        camada1Responde("3480.00");
        camada2Degradada();

        MonitorRunResult r = ciclo.processarMonitor(monitor.getId());

        // D-041: preco nao confirmado nao alerta. Divergencias de 61%, 69% e 81%
        // entre cache e preco real tornariam o alerta quase sempre falso.
        assertThat(r.busca().temOportunidade()).isTrue();
        assertThat(r.busca().camada2Degradada()).isTrue();
        assertThat(r.alerta().alertar()).isFalse();
        assertThat(r.alerta().motivo()).isEqualTo(AlertDecision.Motivo.SEM_CONFIRMACAO);
        assertThat(alertas.findAll()).isEmpty();

        // A execucao da camada 2 fica marcada como PARCIAL: degradou, nao falhou.
        assertThat(execucoes.findAll())
                .filteredOn(e -> e.getSource() == PriceSource.FAST_FLIGHTS)
                .singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(SearchStatus.PARTIAL));
    }

    @Test
    @DisplayName("camada 2 fora do ar antecipa a proxima varredura, sem martelar a fonte")
    void camada2ForaDoArAntecipaRetentativa() {
        camada1Responde("3480.00");
        camada2Degradada();

        // Pelo ciclo, e nao por processarMonitor: quem agenda a proxima busca e
        // a reivindicacao do ciclo. Chamado direto — o caminho do endpoint
        // manual —, o monitor continua vencido, porque o pedido manual nao e
        // dono do calendario.
        ciclo.executarCiclo();

        Monitor recarregado = monitores.findById(monitor.getId()).orElseThrow();
        // A camada 2 costuma voltar em minutos. Esperar as 6h do intervalo faria
        // perder a oportunidade por uma indisponibilidade passageira (D-041);
        // voltar imediatamente martelaria uma fonte que ja esta com problema.
        assertThat(recarregado.getNextSearchAt())
                .isAfter(Instant.now())
                .isBefore(Instant.now().plus(6, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("monitor sem destinatario encontra a oportunidade e nao tem para quem contar")
    void semDestinatarioNaoGeraAlerta() {
        monitores.delete(monitor);
        monitor = salvarMonitor(false);
        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.oportunidades()).isEqualTo(1);
        assertThat(r.alertados()).isZero();
        assertThat(alertas.findAll()).isEmpty();
        // A varredura foi util mesmo assim: o historico continua sendo formado.
        assertThat(observacoes.findAll()).hasSize(2);
    }

    // ------------------------------------------------------ preferencias

    @Test
    @DisplayName("companhia evitada nao vira alerta, mas continua no historico")
    void companhiaEvitadaNaoAlerta() {
        // A camada 1 devolve "IB"; o monitor evita "Iberia". Sao a mesma
        // empresa escrita de dois jeitos, e e justamente esse o caso que uma
        // comparacao literal deixaria passar.
        monitor.evitarCompanhia("Iberia");
        monitores.saveAndFlush(monitor);

        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(r.oportunidades()).isZero();
        assertThat(alertas.findAll()).isEmpty();

        // O historico pertence a ROTA, e nao ao gosto de quem monitora (D-016).
        // As observacoes continuam gravadas e alimentando a estatistica.
        assertThat(observacoes.findAll()).isNotEmpty();
    }

    @Test
    @DisplayName("evitar outra companhia nao atrapalha o alerta")
    void companhiaDiferenteNaoFiltra() {
        monitor.evitarCompanhia("LATAM");
        monitores.saveAndFlush(monitor);

        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        assertThat(ciclo.executarCiclo().alertados()).isEqualTo(1);
    }

    @Test
    @DisplayName("preferir voo direto nao impede o alerta de um voo com escala")
    void prefereDiretoNaoExclui() {
        // A oferta do dublê tem 1 escala. Preferencia nao e exigencia: quem
        // quer exclusao usa maxStops, que nem chega a ser buscado.
        monitor.setPrefereVooDireto(true);
        monitores.saveAndFlush(monitor);

        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        assertThat(ciclo.executarCiclo().alertados()).isEqualTo(1);
    }

    // ------------------------------------------------------------ anti-spam

    @Test
    @DisplayName("o mesmo bom preco duas vezes seguidas gera um alerta, nao dois")
    void cooldownImpedeORealerta() {
        camada1Responde("3480.00");
        camada2Confirma("3720.00");

        ciclo.executarCiclo();
        rearmarMonitor();
        CycleResult segundo = ciclo.executarCiclo();

        assertThat(segundo.oportunidades()).isEqualTo(1);
        assertThat(segundo.alertados()).isZero();
        assertThat(alertas.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("passado o cooldown, so uma queda relevante volta a alertar")
    void aposCooldownExigeQuedaRelevante() {
        camada1Responde("3480.00");
        camada2Confirma("3720.00");
        ciclo.executarCiclo();

        envelhecerAlertas(24);
        rearmarMonitor();
        // 1% abaixo: oscilacao normal de preco de passagem, nao noticia.
        camada2Confirma("3683.00");
        ciclo.executarCiclo();
        assertThat(alertas.findAll()).hasSize(1);

        envelhecerAlertas(24);
        rearmarMonitor();
        // 14% abaixo: isso o usuario quer saber.
        camada2Confirma("3200.00");
        ciclo.executarCiclo();

        assertThat(alertas.findAll()).hasSize(2);
        assertThat(alertas.findAll())
                .anySatisfy(a -> assertThat(a.getMessage()).contains("3.200"));
    }

    /**
     * Devolve o monitor a fila, simulando o intervalo vencido.
     *
     * <p>Via {@code JdbcTemplate}: a classe nao e transacional e a entidade em
     * memoria esta desatualizada apos o ciclo mexer nela.
     */
    private void rearmarMonitor() {
        jdbc.update("update monitor set next_search_at = now() - interval '1 hour'");
    }

    /** Empurra os alertas para tras no tempo, vencendo o cooldown. */
    private void envelhecerAlertas(int horas) {
        jdbc.update("update alert set created_at = created_at - make_interval(hours => ?)", horas);
    }

    // -------------------------------------------------------------- falhas

    @Test
    @DisplayName("worker fora do ar nao inventa alerta nem prende o monitor em laco")
    void workerForaDoArNaoInventaAlerta() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withStatus(502)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\":\"a fonte recusou a consulta\"}")));

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.falha()).isEqualTo(1);
        assertThat(r.sucesso()).isZero();
        assertThat(alertas.findAll()).isEmpty();
        assertThat(observacoes.findAll()).isEmpty();

        // A falha fica registrada com o motivo, em vez de sumir.
        assertThat(execucoes.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(SearchStatus.FAILED);
            assertThat(e.getErrorMessage()).contains("502");
        });

        // Reagendado para antes do intervalo normal, mas NAO vencido: o ciclo
        // seguinte nao pode martelar uma fonte que ja esta com problema.
        Monitor recarregado = monitores.findById(monitor.getId()).orElseThrow();
        assertThat(recarregado.getNextSearchAt()).isAfter(Instant.now());
        assertThat(ciclo.executarCiclo().ocioso()).isTrue();
    }

    @Test
    @DisplayName("a camada 1 sem oferta abaixo do teto encerra o ciclo sem chamar a camada 2")
    void semCandidatoNaoChamaCamada2() {
        camada1Responde("6200.00");
        camada2Confirma("3720.00");

        CycleResult r = ciclo.executarCiclo();

        assertThat(r.sucesso()).isEqualTo(1);
        assertThat(r.oportunidades()).isZero();
        assertThat(alertas.findAll()).isEmpty();
        // Cada confirmacao e uma consulta ao vivo ao Google, sujeita a bloqueio
        // por excesso de requisicoes. Nao chamar quando nao ha candidato e
        // economia de cota, nao detalhe.
        worker.verify(0, postRequestedFor(urlEqualTo("/search/confirm")));
    }
}
