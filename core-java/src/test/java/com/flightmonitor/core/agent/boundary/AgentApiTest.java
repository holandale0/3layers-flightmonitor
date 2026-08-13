package com.flightmonitor.core.agent.boundary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Endpoint do agente — etapa E3.1.
 *
 * <p>O worker entra como WireMock, com o JSON copiado do formato que a
 * interpretação por regras realmente devolve. O contrato de verdade entre as
 * duas linguagens é coberto pelo E2E entre serviços (E1.16); aqui o foco é o
 * que o <b>core</b> faz com a resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentApiTest {

    private static WireMockServer worker;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void subirWorkerFalso() {
        worker = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        worker.start();
    }

    @AfterAll
    static void derrubarWorkerFalso() {
        worker.stop();
    }

    @DynamicPropertySource
    static void apontarParaOWorkerFalso(DynamicPropertyRegistry registry) {
        registry.add("flightmonitor.worker.base-url", () -> "http://localhost:" + worker.port());
    }

    @BeforeEach
    void limpar() {
        worker.resetAll();
    }

    private void workerResponde(String corpo) {
        worker.stubFor(WireMock.post(urlEqualTo("/nlp/intent")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(corpo)));
    }

    private String pedido(String texto) {
        return """
                {"texto": "%s", "origemPadrao": "GRU"}
                """.formatted(texto);
    }

    // ------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("um pedido completo volta pronto para virar monitor")
    void pedidoCompleto() throws Exception {
        workerResponde("""
                {
                  "origin": "GRU",
                  "destination": "LIS",
                  "departure_from": "2027-03-01",
                  "departure_to": "2027-03-31",
                  "min_stay_days": 7,
                  "max_stay_days": 9,
                  "max_price": "4000",
                  "currency": "BRL",
                  "max_stops": null,
                  "passengers": null,
                  "prefere_voo_direto": true,
                  "avoided_airlines": [],
                  "label": "Viagem para LIS",
                  "provider": "regras",
                  "confianca": 1.0,
                  "avisos": []
                }
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Quero ir pra Lisboa em marco por ate 4 mil, voo direto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.faltando", hasSize(0)))
                .andExpect(jsonPath("$.intencao.origin").value("GRU"))
                .andExpect(jsonPath("$.intencao.destination").value("LIS"))
                .andExpect(jsonPath("$.intencao.departureFrom").value("2027-03-01"))
                .andExpect(jsonPath("$.intencao.prefereVooDireto").value(true))
                // Quem interpretou vai na resposta: muda o quanto se pode
                // confiar no resultado.
                .andExpect(jsonPath("$.intencao.provider").value("regras"));
    }

    @Test
    @DisplayName("o snake_case do worker vira camelCase na resposta do core")
    void traducaoDoContrato() throws Exception {
        workerResponde("""
                {"origin":"GRU","destination":"LIS","departure_from":"2027-03-01",
                 "departure_to":"2027-03-31","max_price":"4000","min_stay_days":7,
                 "prefere_voo_direto":true,"provider":"regras","confianca":1.0,
                 "avisos":[],"avoided_airlines":["IB"]}
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("qualquer coisa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencao.minStayDays").value(7))
                .andExpect(jsonPath("$.intencao.avoidedAirlines[0]").value("IB"));
    }

    @Test
    @DisplayName("o core manda a data de hoje, em vez de deixar o worker decidir")
    void mandaADataDeHoje() throws Exception {
        workerResponde("""
                {"origin":"GRU","destination":"LIS","provider":"regras","confianca":0.4,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Lisboa")))
                .andExpect(status().isOk());

        // "Em marco" depende de quando se pergunta. Deixar cada processo ler o
        // proprio relogio faria a resposta variar por causa de fuso ou de
        // container.
        worker.verify(postRequestedFor(urlEqualTo("/nlp/intent"))
                .withRequestBody(matchingJsonPath("$.hoje"))
                .withRequestBody(matchingJsonPath("$.origem_padrao", equalTo("GRU")))
                .withRequestBody(matchingJsonPath("$.texto")));
    }

    // ------------------------------------------------- pedido incompleto

    @Test
    @DisplayName("pedido incompleto diz exatamente o que faltou, em portugues")
    void pedidoIncompleto() throws Exception {
        workerResponde("""
                {"origin":"GRU","destination":null,"departure_from":null,"departure_to":null,
                 "max_price":null,"provider":"regras","confianca":0.2,
                 "avisos":["nao encontrei o periodo da viagem no texto"]}
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("quero viajar barato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.faltando", hasSize(3)))
                // "Interpretacao incompleta" nao ajuda ninguem; dizer o que
                // falta resolve o problema na hora.
                .andExpect(jsonPath("$.sugestao").value(
                        "faltou dizer: destino, periodo da viagem, preco maximo"));
    }

    @Test
    @DisplayName("rota degenerada e apontada como problema, e nao criada")
    void rotaDegenerada() throws Exception {
        workerResponde("""
                {"origin":"GRU","destination":"GRU","departure_from":"2027-03-01",
                 "departure_to":"2027-03-31","max_price":"4000",
                 "provider":"regras","confianca":1.0,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("de Sao Paulo para Guarulhos")))
                .andExpect(status().isOk())
                // Todos os campos vieram, e mesmo assim nao da para criar: o
                // core conhece as regras do que um monitor precisa ser, e o
                // worker so relata o que leu.
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.faltando[0]").value("origem e destino iguais"));
    }

    @Test
    @DisplayName("um campo faltando gera frase no singular")
    void umCampoFaltando() throws Exception {
        workerResponde("""
                {"origin":"GRU","destination":"LIS","departure_from":"2027-03-01",
                 "departure_to":"2027-03-31","max_price":null,
                 "provider":"regras","confianca":0.8,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Lisboa em marco")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugestao").value("faltou dizer: preco maximo"));
    }

    // ------------------------------------------------------------ falhas

    @Test
    @DisplayName("texto curto demais e recusado antes de chegar ao worker")
    void textoCurto() throws Exception {
        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"oi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.texto").exists());

        worker.verify(0, postRequestedFor(urlEqualTo("/nlp/intent")));
    }

    @Test
    @DisplayName("origem padrao invalida e recusada")
    void origemPadraoInvalida() throws Exception {
        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"quero ir pra Lisboa\", \"origemPadrao\": \"Sao Paulo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.origemPadrao").exists());
    }

    @Test
    @DisplayName("worker fora do ar vira 503, e nao intencao vazia")
    void workerForaDoAr() throws Exception {
        worker.stubFor(WireMock.post(urlEqualTo("/nlp/intent"))
                .willReturn(aResponse().withStatus(500)));

        // Devolver uma intencao vazia faria o usuario achar que o pedido dele
        // nao continha nada — pior do que dizer que o servico caiu.
        mvc.perform(post("/api/agent/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Quero ir pra Lisboa em marco por 4 mil")))
                .andExpect(status().isServiceUnavailable());
    }
}
