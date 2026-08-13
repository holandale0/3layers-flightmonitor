package com.flightmonitor.core.search.boundary.client;

import com.flightmonitor.core.search.control.client.WorkerUnavailableException;

import com.flightmonitor.core.search.control.client.SearchClient;


import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Testa o contrato com o worker usando um worker falso (WireMock).
 *
 * <p>As respostas JSON foram copiadas do formato REAL devolvido pelo worker nas
 * etapas E1.5 e E1.6, incluindo o snake_case. Um teste com JSON inventado
 * validaria apenas a nossa propria imaginacao.
 *
 * <p>Sem contexto Spring: o cliente e montado a mao. Testa o adaptador, nao a
 * fiacao — e roda em milissegundos.
 */
class SearchClientTest {

    private static WireMockServer worker;
    private static SearchClient client;

    @BeforeAll
    static void subirWorkerFalso() {
        worker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        worker.start();
        client = clienteApontandoPara(worker.port());
    }

    private static SearchClient clienteApontandoPara(int porta) {
        WorkerProperties props = new WorkerProperties(
                "http://localhost:" + porta,
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                // Este teste e do adaptador REST; o transporte AMQP tem o
                // proprio caminho, coberto pelo E2E entre servicos.
                WorkerProperties.Transporte.REST);

        WorkerClientConfig config = new WorkerClientConfig();
        return new RestSearchClient(
                config.workerScanClient(props), config.workerConfirmClient(props));
    }

    /**
     * Porta livre para simular worker fora do ar.
     *
     * <p>Derrubar o WireMock compartilhado seria mais direto, mas com porta
     * dinamica ele volta em OUTRA porta e quebra os testes seguintes — o que
     * de fato aconteceu na primeira execucao desta suite.
     */
    private static int portaSemNinguemEscutando() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @AfterAll
    static void derrubarWorkerFalso() {
        worker.stop();
    }

    @BeforeEach
    void limpar() {
        worker.resetAll();
    }

    private CalendarSearchCommand varredura() {
        return new CalendarSearchCommand(
                "GRU", "LIS",
                LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 20),
                null, null, "BRL", (short) 1);
    }

    private ConfirmCommand confirmacao() {
        return new ConfirmCommand(
                "GRU", "LIS",
                LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 27),
                "BRL", (short) 1, (short) 1, new BigDecimal("3375"));
    }

    // ------------------------------------------------------------ camada 1

    @Test
    @DisplayName("varredura desserializa o snake_case do worker")
    void varreduraDesserializa() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "origin": "GRU",
                          "destination": "LIS",
                          "offers": [{
                            "departure_date": "2027-03-12",
                            "return_date": "2027-03-27",
                            "price": "2980",
                            "currency": "BRL",
                            "airline": "TP",
                            "flight_number": "1234",
                            "stops": 0,
                            "departure_at": "2027-03-12T22:30:00",
                            "arrival_at": "2027-03-27T10:15:00",
                            "expires_at": "2026-08-09T20:14:40Z",
                            "source": "TRAVELPAYOUTS"
                          }],
                          "returned": 30,
                          "kept": 1,
                          "provider_origin": "SAO",
                          "provider_destination": "LIS",
                          "warnings": ["a fonte respondeu com o codigo de cidade SAO"]
                        }
                        """)));

        CalendarSearchResult r = client.scanCalendar(varredura());

        assertThat(r.origin()).isEqualTo("GRU");
        assertThat(r.returned()).isEqualTo(30);
        assertThat(r.kept()).isEqualTo(1);
        assertThat(r.providerOrigin()).isEqualTo("SAO");
        assertThat(r.warnings()).hasSize(1);

        assertThat(r.offers()).singleElement().satisfies(o -> {
            assertThat(o.departureDate()).isEqualTo(LocalDate.of(2027, 3, 12));
            assertThat(o.price()).isEqualByComparingTo("2980");
            assertThat(o.stops()).isZero();
            // Horario de voo e local: sem fuso.
            assertThat(o.departureAt()).isEqualTo(LocalDateTime.of(2027, 3, 12, 22, 30));
            // expires_at e um instante: com fuso.
            assertThat(o.expiresAt()).isNotNull();
            assertThat(o.source()).isEqualTo("TRAVELPAYOUTS");
        });
    }

    @Test
    @DisplayName("o pedido sai em snake_case, como o worker espera")
    void pedidoSaiEmSnakeCase() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"origin":"GRU","destination":"LIS","offers":[],"returned":0,"kept":0}
                        """)));

        client.scanCalendar(varredura());

        worker.verify(postRequestedFor(urlEqualTo("/search/calendar"))
                .withRequestBody(matchingJsonPath("$.departure_from", equalTo("2027-03-10")))
                .withRequestBody(matchingJsonPath("$.departure_to", equalTo("2027-03-20")))
                .withRequestBody(matchingJsonPath("$.max_stops", equalTo("1"))));
    }

    @Test
    @DisplayName("resposta sem ofertas nao quebra: listas nulas viram vazias")
    void listasNulasViramVazias() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"origin":"GRU","destination":"LIS","returned":0,"kept":0}
                        """)));

        CalendarSearchResult r = client.scanCalendar(varredura());

        assertThat(r.offers()).isEmpty();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("vazioAposFiltro distingue janela sem oferta de fonte fora do ar")
    void distingueVazioDeFonteMorta() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"origin":"GRU","destination":"LIS","offers":[],"returned":30,"kept":0}
                        """)));

        assertThat(client.scanCalendar(varredura()).vazioAposFiltro()).isTrue();
    }

    @Test
    @DisplayName("HTTP 502 do worker vira WorkerUnavailableException")
    void erroDoWorkerNaVarreduraLanca() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withStatus(502)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\":\"a fonte recusou a consulta\"}")));

        assertThatThrownBy(() -> client.scanCalendar(varredura()))
                .isInstanceOf(WorkerUnavailableException.class)
                .hasMessageContaining("502");
    }

    @Test
    @DisplayName("worker fora do ar na varredura lanca, pois sem preco nao ha varredura")
    void workerForaDoArNaVarreduraLanca() throws IOException {
        SearchClient semWorker = clienteApontandoPara(portaSemNinguemEscutando());

        assertThatThrownBy(() -> semWorker.scanCalendar(varredura()))
                .isInstanceOf(WorkerUnavailableException.class)
                .hasMessageContaining("nao foi possivel falar com o worker");
    }

    @Test
    @DisplayName("timeout na varredura lanca")
    void timeoutNaVarreduraLanca() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withFixedDelay(4000)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        assertThatThrownBy(() -> client.scanCalendar(varredura()))
                .isInstanceOf(WorkerUnavailableException.class);
    }

    // ------------------------------------------------------------ camada 2

    @Test
    @DisplayName("confirmacao desserializa a oferta e as tentativas")
    void confirmacaoDesserializa() {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "confirmed": true,
                          "degraded": false,
                          "offer": {
                            "departure_date": "2027-03-12",
                            "return_date": "2027-03-27",
                            "price": "5438",
                            "currency": "BRL",
                            "airline": "Tap Air Portugal",
                            "airline_code": "TP",
                            "stops": 0,
                            "duration_minutes": 590,
                            "departure_at": null,
                            "arrival_at": "2027-03-12T14:35:00",
                            "departure_airport": "GRU",
                            "arrival_airport": "LIS",
                            "source": "FAST_FLIGHTS"
                          },
                          "provider": "fast-flights",
                          "attempts": [
                            {"provider":"fast-flights","ok":true,"found":true,"error":null,"duration_ms":1140}
                          ],
                          "warnings": ["preco real 61% acima do candidato da camada 1"]
                        }
                        """)));

        ConfirmResult r = client.confirm(confirmacao());

        assertThat(r.confirmed()).isTrue();
        assertThat(r.degraded()).isFalse();
        assertThat(r.naoExiste()).isFalse();
        assertThat(r.provider()).isEqualTo("fast-flights");
        assertThat(r.offer().airline()).isEqualTo("Tap Air Portugal");
        assertThat(r.offer().airlineCode()).isEqualTo("TP");
        assertThat(r.offer().departureAirport()).isEqualTo("GRU");
        assertThat(r.offer().durationMinutes()).isEqualTo(590);
        // A biblioteca as vezes nao traz a hora — o campo nulo precisa sobreviver.
        assertThat(r.offer().departureAt()).isNull();
        assertThat(r.attempts()).singleElement()
                .satisfies(a -> assertThat(a.durationMs()).isEqualTo(1140));
        assertThat(r.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("candidate_price vai no pedido, para o worker medir a divergencia")
    void enviaPrecoCandidato() {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"confirmed\":false,\"degraded\":true}")));

        client.confirm(confirmacao());

        worker.verify(postRequestedFor(urlEqualTo("/search/confirm"))
                .withRequestBody(matchingJsonPath("$.candidate_price"))
                .withRequestBody(matchingJsonPath("$.departure_date", equalTo("2027-03-12"))));
    }

    @Test
    @DisplayName("'voo nao existe' e distinguido de 'nao consegui verificar'")
    void distingueNaoExisteDeDegradado() {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"confirmed": false, "degraded": false, "provider": "fast-flights",
                         "warnings": ["o candidato da camada 1 nao se sustentou"]}
                        """)));

        ConfirmResult r = client.confirm(confirmacao());

        assertThat(r.confirmed()).isFalse();
        assertThat(r.degraded()).isFalse();
        assertThat(r.naoExiste()).isTrue();
    }

    @Test
    @DisplayName("worker fora do ar na confirmacao DEGRADA, nao lanca")
    void workerForaDoArNaConfirmacaoDegrada() throws IOException {
        SearchClient semWorker = clienteApontandoPara(portaSemNinguemEscutando());

        ConfirmResult r = semWorker.confirm(confirmacao());

        assertThat(r.degraded()).isTrue();
        assertThat(r.confirmed()).isFalse();
        assertThat(r.naoExiste()).isFalse();
        assertThat(r.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("HTTP 500 na confirmacao DEGRADA, nao lanca")
    void erroHttpNaConfirmacaoDegrada() {
        worker.stubFor(post(urlEqualTo("/search/confirm"))
                .willReturn(aResponse().withStatus(500)));

        ConfirmResult r = client.confirm(confirmacao());

        assertThat(r.degraded()).isTrue();
        assertThat(r.warnings()).anyMatch(w -> w.contains("500"));
    }

    @Test
    @DisplayName("timeout na confirmacao DEGRADA, nao lanca")
    void timeoutNaConfirmacaoDegrada() {
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withFixedDelay(4000)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        assertThat(client.confirm(confirmacao()).degraded()).isTrue();
    }

    @Test
    @DisplayName("os dois endpoints do worker sao os esperados")
    void enderecosDoContrato() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"origin\":\"GRU\",\"destination\":\"LIS\",\"returned\":0,\"kept\":0}")));
        worker.stubFor(post(urlEqualTo("/search/confirm")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"confirmed\":false,\"degraded\":true}")));

        client.scanCalendar(varredura());
        client.confirm(confirmacao());

        worker.verify(postRequestedFor(urlEqualTo("/search/calendar")));
        worker.verify(postRequestedFor(urlEqualTo("/search/confirm")));
    }

    @Test
    @DisplayName("corpo da varredura contem a rota pedida")
    void corpoContemRota() {
        worker.stubFor(post(urlEqualTo("/search/calendar")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"origin\":\"GRU\",\"destination\":\"LIS\",\"returned\":0,\"kept\":0}")));

        client.scanCalendar(varredura());

        worker.verify(postRequestedFor(urlEqualTo("/search/calendar"))
                .withRequestBody(equalToJson("""
                        {"origin":"GRU","destination":"LIS","departure_from":"2027-03-10",
                         "departure_to":"2027-03-20","return_from":null,"return_to":null,
                         "currency":"BRL","max_stops":1}
                        """, true, true)));
    }
}
