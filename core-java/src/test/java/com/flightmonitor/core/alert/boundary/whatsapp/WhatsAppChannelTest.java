package com.flightmonitor.core.alert.boundary.whatsapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.control.AlertMessageFormatter;
import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import tools.jackson.databind.ObjectMapper;

/**
 * Testa o adaptador do WhatsApp contra uma Graph API falsa.
 *
 * <p>As respostas de erro reproduzem os codigos reais documentados pela Meta —
 * os mesmos listados em docs/GUIA-WHATSAPP.md. Nao ha rede envolvida.
 *
 * <p>O foco esta na CLASSIFICACAO das falhas: o que decide entre retentar e
 * desistir. Enviar com sucesso e o caso facil; distinguir "numero nao
 * verificado" de "a Meta caiu" e o que evita alerta perdido e cota gasta.
 */
class WhatsAppChannelTest {

    private static WireMockServer meta;
    private static WhatsAppNotificationChannel canal;

    private static final String CAMINHO = "/v21.0/123456789012345/messages";

    @BeforeAll
    static void subirMetaFalsa() {
        meta = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        meta.start();

        WhatsAppProperties props = new WhatsAppProperties(
                "123456789012345",
                "EAAtoken-de-teste",
                "http://localhost:" + meta.port(),
                "v21.0",
                "alerta_preco_voo",
                "pt_BR",
                "token-de-verificacao",
                null,
                Duration.ofSeconds(2));

        canal = new WhatsAppNotificationChannel(props, new AlertMessageFormatter(), new ObjectMapper());
    }

    @AfterAll
    static void derrubarMetaFalsa() {
        meta.stop();
    }

    @BeforeEach
    void limpar() {
        meta.resetAll();
    }

    private Alert alerta() {
        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setMaxPrice(new BigDecimal("9000.00"));
        m.setCurrency("BRL");

        PriceObservation o = new PriceObservation(
                m, "GRU", "LIS", LocalDate.of(2026, 9, 25),
                new BigDecimal("5602.00"), PriceSource.FAST_FLIGHTS);
        o.setReturnDate(LocalDate.of(2026, 10, 8));
        o.setCurrency("BRL");
        o.setAirline("Air Europa");
        o.setStops((short) 1);
        o.setConfirmed(true);

        Recipient r = new Recipient("Leonardo", "+5511987654321");

        Alert a = new Alert(m, o, r, "texto do canal de log");
        a.setChannel(AlertChannel.WHATSAPP);
        return a;
    }

    private void metaResponde(int status, String corpo) {
        meta.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(corpo)));
    }

    private void metaAceita() {
        metaResponde(200, """
                {
                  "messaging_product": "whatsapp",
                  "contacts": [{"input": "5511987654321", "wa_id": "5511987654321"}],
                  "messages": [{"id": "wamid.HBgNNTUxMTk4NzY1NDMyMRUCABEYEjhE"}]
                }
                """);
    }

    private String erroDaMeta(int codigo, String mensagem) {
        return """
                {"error": {"message": "%s", "type": "OAuthException",
                 "code": %d, "fbtrace_id": "Axxxxxxxxxx"}}
                """.formatted(mensagem, codigo);
    }

    // ------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("entrega devolve o wamid como identificador do provedor")
    void entregaComSucesso() {
        metaAceita();

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isTrue();
        assertThat(r.providerMessageId()).startsWith("wamid.");
        assertThat(r.erro()).isNull();
    }

    @Test
    @DisplayName("envia TEMPLATE, e nao texto livre")
    void enviaTemplate() {
        metaAceita();

        canal.enviar(alerta());

        meta.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withRequestBody(matchingJsonPath("$.type", equalTo("template")))
                .withRequestBody(matchingJsonPath("$.template.name", equalTo("alerta_preco_voo")))
                .withRequestBody(matchingJsonPath("$.template.language.code", equalTo("pt_BR"))));
    }

    @Test
    @DisplayName("o telefone vai sem o + do E.164")
    void telefoneSemMais() {
        metaAceita();

        canal.enviar(alerta());

        meta.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withRequestBody(matchingJsonPath("$.to", equalTo("5511987654321"))));
    }

    @Test
    @DisplayName("o token vai no cabecalho Authorization")
    void tokenNoCabecalho() {
        metaAceita();

        canal.enviar(alerta());

        meta.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withHeader("Authorization", equalTo("Bearer EAAtoken-de-teste")));
    }

    @Test
    @DisplayName("os cinco parametros vao na ordem do template aprovado")
    void cincoParametrosNaOrdem() {
        metaAceita();

        canal.enviar(alerta());

        // Cinco e nao seis: a Meta recusou a versao com origem e destino
        // separados, por "proporcao entre palavras e parametros".
        meta.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withRequestBody(matchingJsonPath("$.template.components[0].type", equalTo("body")))
                .withRequestBody(matchingJsonPath(
                        "$.template.components[0].parameters[0].text", equalTo("GRU para LIS")))
                .withRequestBody(matchingJsonPath(
                        "$.template.components[0].parameters[1].text",
                        equalTo("25/09/2026 a 08/10/2026")))
                .withRequestBody(matchingJsonPath(
                        "$.template.components[0].parameters[2].text",
                        equalTo("Air Europa, 1 escala")))
                .withRequestBody(matchingJsonPath(
                        "$.template.components[0].parameters[3].text", equalTo("R$ 5.602,00")))
                .withRequestBody(matchingJsonPath(
                        "$.template.components[0].parameters[4].text", equalTo("R$ 9.000,00"))));
    }

    // ------------------------------------------------- erros PERMANENTES

    @Test
    @DisplayName("numero nao verificado: falha PERMANENTE, retentar nao adianta")
    void numeroNaoVerificado() {
        metaResponde(400, erroDaMeta(MetaErro.DESTINATARIO_NAO_PERMITIDO,
                "Recipient phone number not in allowed list"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("destinatarios verificados");
    }

    @Test
    @DisplayName("token expirado: falha PERMANENTE, com dica sobre o token de 24h")
    void tokenExpirado() {
        metaResponde(401, erroDaMeta(MetaErro.TOKEN_INVALIDO, "Access token has expired"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("24h");
    }

    @Test
    @DisplayName("template inexistente: falha PERMANENTE")
    void templateInexistente() {
        metaResponde(400, erroDaMeta(MetaErro.TEMPLATE_INEXISTENTE,
                "Template name does not exist in the translation"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("template nao encontrado");
    }

    @Test
    @DisplayName("texto livre fora da janela: falha PERMANENTE explicando o template")
    void foraDaJanelaDe24h() {
        metaResponde(400, erroDaMeta(MetaErro.FORA_DA_JANELA, "Re-engagement message"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("template aprovado");
    }

    @Test
    @DisplayName("4xx desconhecido e tratado como permanente")
    void erro4xxDesconhecido() {
        metaResponde(400, erroDaMeta(999999, "Algo que ainda nao mapeamos"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
    }

    // ------------------------------------------------- erros TRANSITORIOS

    @Test
    @DisplayName("limite de taxa: falha TRANSITORIA, vale tentar depois")
    void limiteDeTaxa() {
        metaResponde(429, erroDaMeta(MetaErro.LIMITE_DE_TAXA, "Rate limit hit"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isTrue();
    }

    @Test
    @DisplayName("erro interno da Meta: falha TRANSITORIA")
    void erroInternoDaMeta() {
        metaResponde(500, erroDaMeta(MetaErro.ERRO_INTERNO, "Something went wrong"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isTrue();
    }

    @Test
    @DisplayName("5xx sem corpo reconhecivel e transitorio")
    void erro5xxSemCorpo() {
        metaResponde(503, "<html>Service Unavailable</html>");

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isTrue();
    }

    @Test
    @DisplayName("timeout e transitorio")
    void timeout() {
        meta.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse()
                .withFixedDelay(4000)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isTrue();
    }

    @Test
    @DisplayName("HTTP 200 sem id nao vira sucesso inventado")
    void respostaSemId() {
        metaResponde(200, "{\"messaging_product\":\"whatsapp\"}");

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isTrue();
        assertThat(r.erro()).contains("sem id");
    }

    // ------------------------------------------------------- configuracao

    @Test
    @DisplayName("sem credenciais, recusa com mensagem que aponta o guia")
    void semCredenciais() {
        WhatsAppProperties vazio = new WhatsAppProperties(
                null, null, "http://localhost:1", "v21.0", "alerta_preco_voo", "pt_BR",
                null, null, Duration.ofSeconds(1));
        WhatsAppNotificationChannel semCred =
                new WhatsAppNotificationChannel(vazio, new AlertMessageFormatter(), new ObjectMapper());

        DeliveryResult r = semCred.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("GUIA-WHATSAPP");
    }

    @Test
    @DisplayName("alerta sem monitor recusa em vez de montar template quebrado")
    void alertaSemMonitor() {
        Alert a = alerta();
        a.setMonitor(null);

        DeliveryResult r = canal.enviar(a);

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("sem monitor");
    }

    @Test
    @DisplayName("o canal declarado e WHATSAPP")
    void declaraOCanal() {
        assertThat(canal.canal()).isEqualTo(AlertChannel.WHATSAPP);
    }
}
