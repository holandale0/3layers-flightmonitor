package com.flightmonitor.core.agent.boundary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Criação de monitor por conversa — etapa E3.2.
 *
 * <p>Worker em WireMock, banco real. O que se testa aqui é o que o <b>core</b>
 * faz com uma intenção: recusar, assumir, avisar e criar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentCriacaoTest {

    private static WireMockServer worker;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

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
    void preparar() {
        limparBanco();
        worker.resetAll();
    }

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        monitores.deleteAll();
        destinatarios.deleteAll();
    }

    /** Intenção completa, com a rota POA→OPO — usada só aqui. */
    private void workerEntende() {
        workerResponde("""
                {"origin":"POA","destination":"OPO",
                 "departure_from":"2027-05-01","departure_to":"2027-05-31",
                 "max_price":"3200","currency":"BRL","min_stay_days":7,"max_stay_days":9,
                 "prefere_voo_direto":true,"avoided_airlines":["IB"],
                 "label":"Viagem para OPO","provider":"regras","confianca":1.0,"avisos":[]}
                """);
    }

    private void workerResponde(String corpo) {
        worker.stubFor(WireMock.post(urlEqualTo("/nlp/intent")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(corpo)));
    }

    private String pedido(String texto) {
        return """
                {"texto": "%s", "origemPadrao": "POA"}
                """.formatted(texto);
    }

    // ------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("uma frase vira monitor, com as preferências junto")
    void fraseViraMonitor() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Quero ir pro Porto em maio por ate 3200, voo direto, uma semana, sem Iberia")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.criado.origin").value("POA"))
                .andExpect(jsonPath("$.criado.destination").value("OPO"))
                .andExpect(jsonPath("$.criado.maxPrice").value(3200))
                .andExpect(jsonPath("$.criado.minStayDays").value(7))
                // As preferências da E2.6 atravessam a conversa até o monitor.
                .andExpect(jsonPath("$.criado.prefereVooDireto").value(true))
                .andExpect(jsonPath("$.criado.avoidedAirlines[0]").value("IB"));

        assertThat(monitores.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("o monitor nasce ativo e pronto para a primeira varredura")
    void monitorNasceAtivo() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criado.active").value(true));

        Monitor criado = monitores.findAll().get(0);
        // Criado com nextSearchAt=agora: sem isso o monitor esperaria o
        // intervalo inteiro antes da primeira busca, e a pessoa acharia que
        // nao funcionou.
        assertThat(criado.getNextSearchAt()).isBeforeOrEqualTo(java.time.Instant.now());
    }

    // ---------------------------------------------- nada e assumido em silencio

    @Test
    @DisplayName("tudo que o sistema escolheu sozinho aparece na resposta")
    void oQueFoiAssumidoEDito() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated())
                // Padrao invisivel e a forma mais educada de mentir.
                .andExpect(jsonPath("$.assumido", hasSize(4)))
                .andExpect(jsonPath("$.assumido[0]", containsString("Leonardo")))
                .andExpect(jsonPath("$.assumido[1]").value("1 passageiro"))
                .andExpect(jsonPath("$.assumido[2]").value("varredura a cada 6 horas"));
    }

    @Test
    @DisplayName("com um destinatário ativo só, ele é usado — e isso é dito")
    void destinatarioUnicoEUsado() throws Exception {
        Recipient unico = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criado.recipients", hasSize(1)))
                .andExpect(jsonPath("$.criado.recipients[0].id").value(unico.getId()));
    }

    @Test
    @DisplayName("com vários destinatários, não escolhe — e avisa que ninguém será avisado")
    void variosDestinatariosNaoEscolhe() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        destinatarios.saveAndFlush(new Recipient("Outra pessoa", "+5511911112222"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criado.recipients", hasSize(0)))
                // Sem esta frase, o silêncio do monitor pareceria "não achei
                // nada barato" — e não "não tem para quem avisar".
                .andExpect(jsonPath("$.avisos[0]", containsString("nao tem destinatario")));
    }

    @Test
    @DisplayName("destinatário informado no pedido vence a escolha automática")
    void destinatarioInformadoVence() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Nao e esse", "+5511999990000"));
        Recipient escolhido = destinatarios.saveAndFlush(new Recipient("Esse", "+5511911112222"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Porto em maio por 3200","origemPadrao":"POA","recipientIds":[%d]}
                                """.formatted(escolhido.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criado.recipients", hasSize(1)))
                .andExpect(jsonPath("$.criado.recipients[0].id").value(escolhido.getId()));
    }

    // ------------------------------------------------------ pedido incompleto

    @Test
    @DisplayName("pedido incompleto não cria nada, e diz o que faltou")
    void pedidoIncompletoNaoCria() throws Exception {
        workerResponde("""
                {"origin":"POA","destination":null,"departure_from":null,"departure_to":null,
                 "max_price":null,"provider":"regras","confianca":0.2,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("quero viajar barato")))
                // 422 e nao 400: a sintaxe do pedido esta certa, o conteudo e
                // que nao basta. Para o painel sao duas coisas diferentes.
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.sucesso").value(false))
                .andExpect(jsonPath("$.criado").doesNotExist())
                .andExpect(jsonPath("$.faltando", hasSize(3)))
                .andExpect(jsonPath("$.mensagem", containsString("faltou")))
                // A interpretação volta junto: a interface pergunta só o que
                // falta, em vez de mandar reescrever tudo.
                .andExpect(jsonPath("$.intencao.origin").value("POA"));

        assertThat(monitores.findAll()).isEmpty();
    }

    @Test
    @DisplayName("origem igual ao destino é recusada, mesmo com todos os campos")
    void rotaDegeneradaNaoCria() throws Exception {
        workerResponde("""
                {"origin":"POA","destination":"POA","departure_from":"2027-05-01",
                 "departure_to":"2027-05-31","max_price":"3200",
                 "provider":"regras","confianca":1.0,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("de Porto Alegre para Porto Alegre em maio")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.faltando[0]").value("origem e destino iguais"));

        assertThat(monitores.findAll()).isEmpty();
    }

    // ---------------------------------------------------------- duplicata

    @Test
    @DisplayName("a mesma frase duas vezes não cria dois monitores")
    void naoDuplica() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated());

        // Reenviar e o acidente mais provavel de um endpoint conversacional, e
        // dois monitores iguais dobram as buscas e os alertas.
        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("ja existe um monitor ativo")));

        assertThat(monitores.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("janela que se sobrepõe também conta como duplicata")
    void janelaSobrepostaEDuplicata() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated());

        // "Porto em maio" e "Porto entre 10 e 20 de maio" sao o mesmo pedido
        // dito de dois jeitos.
        workerResponde("""
                {"origin":"POA","destination":"OPO","departure_from":"2027-05-10",
                 "departure_to":"2027-05-20","max_price":"3000",
                 "provider":"regras","confianca":1.0,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto entre 10 e 20 de maio por 3000")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("outro período na mesma rota é monitor novo, e não duplicata")
    void periodoDiferenteNaoEDuplicata() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated());

        workerResponde("""
                {"origin":"POA","destination":"OPO","departure_from":"2027-09-01",
                 "departure_to":"2027-09-30","max_price":"3200",
                 "provider":"regras","confianca":1.0,"avisos":[]}
                """);

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em setembro por 3200")))
                .andExpect(status().isCreated());

        assertThat(monitores.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("monitor inativo não bloqueia a criação de um novo")
    void monitorInativoNaoBloqueia() throws Exception {
        destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));

        Monitor antigo = new Monitor();
        antigo.setOrigin("POA");
        antigo.setDestination("OPO");
        antigo.setDepartureWindowStart(LocalDate.of(2027, 5, 1));
        antigo.setDepartureWindowEnd(LocalDate.of(2027, 5, 31));
        antigo.setMaxPrice(new BigDecimal("3200"));
        antigo.setActive(false);
        monitores.saveAndFlush(antigo);

        workerEntende();

        // Um monitor desligado nao vai buscar nem alertar: nao ha duplicacao.
        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ falhas

    @Test
    @DisplayName("worker fora do ar vira 503, e nada é criado")
    void workerForaDoAr() throws Exception {
        worker.stubFor(WireMock.post(urlEqualTo("/nlp/intent"))
                .willReturn(aResponse().withStatus(500)));

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("Porto em maio por 3200")))
                .andExpect(status().isServiceUnavailable());

        assertThat(monitores.findAll()).isEmpty();
    }

    @Test
    @DisplayName("destinatário inexistente é erro claro, e não monitor mudo")
    void destinatarioInexistente() throws Exception {
        workerEntende();

        mvc.perform(post("/api/agent/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Porto em maio por 3200","origemPadrao":"POA","recipientIds":[999999]}
                                """))
                .andExpect(status().isNotFound());

        assertThat(monitores.findAll()).isEmpty();
    }
}
