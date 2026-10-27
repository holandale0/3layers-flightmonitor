package com.flightmonitor.core.alert.boundary.push;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.flightmonitor.core.alert.control.AlertMessageFormatter;
import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.recipient.entity.PushDevice;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import tools.jackson.databind.ObjectMapper;

/**
 * O adaptador de push contra um FCM falso.
 *
 * <p>As respostas de erro reproduzem o formato documentado da API HTTP v1, com
 * o {@code errorCode} especifico em {@code details}. Como no teste do WhatsApp,
 * o foco e a CLASSIFICACAO: um token morto desliga um aparelho; um erro nosso,
 * tratado igual, desligaria todos.
 */
class PushChannelTest {

    private static final String CAMINHO = "/v1/projects/projeto-teste/messages:send";

    private static WireMockServer fcm;
    private static FcmClient cliente;

    private PushDeviceRepository aparelhos;
    private PushNotificationChannel canal;
    private Recipient leonardo;

    /** Credencial de mentira: o projeto e um token fixo, sem Google no meio. */
    private record CredencialFalsa(boolean configurada) implements CredencialDoFirebase {
        @Override
        public String projeto() {
            return "projeto-teste";
        }

        @Override
        public String tokenDeAcesso() throws IOException {
            return "ya29.token-de-teste";
        }
    }

    @BeforeAll
    static void subirFcmFalso() {
        fcm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        fcm.start();
        cliente = new FcmClient(
                new PushProperties(null, "http://localhost:" + fcm.port(), Duration.ofSeconds(2)),
                new CredencialFalsa(true),
                new ObjectMapper());
    }

    @AfterAll
    static void derrubarFcmFalso() {
        fcm.stop();
    }

    @BeforeEach
    void preparar() {
        fcm.resetAll();
        aparelhos = Mockito.mock(PushDeviceRepository.class);
        canal = new PushNotificationChannel(cliente, aparelhos);

        leonardo = new Recipient("Leonardo", "+5511987654321");
        ReflectionTestUtils.setField(leonardo, "id", 7L);
    }

    private PushDevice aparelho(long id, String token, String nome) {
        PushDevice d = new PushDevice(leonardo, token, nome);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private void comAparelhos(PushDevice... lista) {
        Mockito.when(aparelhos.findByRecipientIdAndActiveTrue(7L)).thenReturn(List.of(lista));
    }

    private Alert alerta() {
        Monitor m = new Monitor();
        ReflectionTestUtils.setField(m, "id", 42L);
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setMaxPrice(new BigDecimal("9000.00"));
        m.setCurrency("BRL");

        PriceObservation o = new PriceObservation(
                m, "GRU", "LIS", LocalDate.of(2026, 9, 25),
                new BigDecimal("5602.00"), PriceSource.FAST_FLIGHTS);
        o.setCurrency("BRL");

        Alert a = new Alert(m, o, leonardo, "✈️ *Oportunidade encontrada*\n\nGRU → LIS\n*R$ 5.602,00*");
        a.setChannel(AlertChannel.PUSH);
        return a;
    }

    private void responde(String token, int status, String corpo) {
        fcm.stubFor(post(urlEqualTo(CAMINHO))
                .withRequestBody(matchingJsonPath("$.message.token", equalTo(token)))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private static String erroFcm(int code, String status, String errorCode, String mensagem) {
        return """
                {"error": {"code": %d, "message": "%s", "status": "%s",
                  "details": [{"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
                               "errorCode": "%s"}]}}
                """.formatted(code, mensagem, status, errorCode);
    }

    // ---------------------------------------------------------- envio

    @Test
    @DisplayName("envia titulo, corpo sem marcacao, prioridade alta e o caminho da tela")
    void enviaOPayloadCerto() {
        comAparelhos(aparelho(1, "token-do-pixel-000000001", "Pixel 8"));
        responde("token-do-pixel-000000001", 200, "{\"name\": \"projects/projeto-teste/messages/0:1\"}");

        Alert alerta = alerta();
        DeliveryResult r = canal.enviar(alerta);

        assertThat(r.sucesso()).isTrue();
        assertThat(r.providerMessageId()).isEqualTo("projects/projeto-teste/messages/0:1");
        // O titulo vem do mesmo formatador do assunto do e-mail. Comparado com
        // ele, e nao com um literal: o NumberFormat do portugues poe um espaco
        // NAO separavel entre "R$" e o valor, invisivel num literal.
        String titulo = AlertMessageFormatter.titulo(alerta.getPriceObservation());
        assertThat(titulo).startsWith("✈ GRU → LIS por R$");
        fcm.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withHeader("Authorization", equalTo("Bearer ya29.token-de-teste"))
                .withRequestBody(matchingJsonPath("$.message.notification.title", equalTo(titulo)))
                .withRequestBody(matchingJsonPath("$.message.notification.body", equalTo("GRU → LIS\nR$ 5.602,00")))
                .withRequestBody(matchingJsonPath("$.message.android.priority", equalTo("HIGH")))
                .withRequestBody(matchingJsonPath("$.message.android.notification.channel_id", equalTo("alertas")))
                .withRequestBody(matchingJsonPath("$.message.data.caminho", equalTo("/monitores/42"))));
    }

    @Test
    @DisplayName("basta um aparelho receber para o alerta contar como entregue")
    void umAparelhoBasta() {
        comAparelhos(
                aparelho(1, "token-do-tablet-00000001", "Tablet"),
                aparelho(2, "token-do-pixel-000000002", "Pixel 8"));
        responde("token-do-tablet-00000001", 503, erroFcm(503, "UNAVAILABLE", "UNAVAILABLE", "tente depois"));
        responde("token-do-pixel-000000002", 200, "{\"name\": \"projects/projeto-teste/messages/0:2\"}");

        assertThat(canal.enviar(alerta()).sucesso()).isTrue();
    }

    // ---------------------------------------------------- classificacao

    @Test
    @DisplayName("UNREGISTERED desliga o aparelho e falha em definitivo")
    void tokenMortoDesligaOAparelho() {
        comAparelhos(aparelho(1, "token-desinstalado-000001", "Pixel antigo"));
        responde("token-desinstalado-000001", 404,
                erroFcm(404, "NOT_FOUND", "UNREGISTERED", "Requested entity was not found."));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isFalse();
        Mockito.verify(aparelhos).desativar(Mockito.eq(1L), Mockito.contains("UNREGISTERED"));
    }

    @Test
    @DisplayName("INVALID_ARGUMENT que nao fala do token NAO desliga o aparelho")
    void payloadRuimNaoPuneOAparelho() {
        // O mesmo codigo vale para token malformado e para payload malformado.
        // O segundo e defeito nosso: desligar o aparelho por ele apagaria,
        // um a um, todos os celulares que recebem alerta.
        comAparelhos(aparelho(1, "token-valido-000000000001", "Pixel 8"));
        responde("token-valido-000000000001", 400,
                erroFcm(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT", "Invalid value at 'message.data'"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        Mockito.verify(aparelhos, Mockito.never()).desativar(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    @DisplayName("token malformado e culpa do aparelho")
    void tokenMalformado() {
        comAparelhos(aparelho(1, "token-cortado-0000000001", "Pixel 8"));
        responde("token-cortado-0000000001", 400, erroFcm(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT",
                "The registration token is not a valid FCM registration token"));

        canal.enviar(alerta());

        Mockito.verify(aparelhos).desativar(Mockito.eq(1L), Mockito.anyString());
    }

    @Test
    @DisplayName("FCM fora do ar e transitorio")
    void foraDoArRetenta() {
        comAparelhos(aparelho(1, "token-valido-000000000002", "Pixel 8"));
        responde("token-valido-000000000002", 503, erroFcm(503, "UNAVAILABLE", "UNAVAILABLE", "indisponivel"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.sucesso()).isFalse();
        assertThat(r.transitorio()).isTrue();
    }

    @Test
    @DisplayName("credencial recusada e permanente e nao desliga ninguem")
    void credencialRecusada() {
        comAparelhos(aparelho(1, "token-valido-000000000003", "Pixel 8"));
        responde("token-valido-000000000003", 403,
                erroFcm(403, "PERMISSION_DENIED", "THIRD_PARTY_AUTH_ERROR", "Permission denied"));

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        Mockito.verify(aparelhos, Mockito.never()).desativar(Mockito.anyLong(), Mockito.anyString());
    }

    // ------------------------------------------------- sem como enviar

    @Test
    @DisplayName("destinatario sem aparelho falha em definitivo, dizendo o que fazer")
    void semAparelho() {
        comAparelhos();

        DeliveryResult r = canal.enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("Leonardo").contains("Receber neste aparelho");
        fcm.verify(0, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    @DisplayName("sem conta de servico, recusa sem ir ao FCM")
    void semCredencial() {
        FcmClient semChave = new FcmClient(
                new PushProperties(null, "http://localhost:" + fcm.port(), Duration.ofSeconds(2)),
                new CredencialFalsa(false), new ObjectMapper());
        comAparelhos(aparelho(1, "token-valido-000000000004", "Pixel 8"));

        DeliveryResult r = new PushNotificationChannel(semChave, aparelhos).enviar(alerta());

        assertThat(r.transitorio()).isFalse();
        assertThat(r.erro()).contains("FCM_CREDENCIAIS");
        fcm.verify(0, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    // ---------------------------------------------------------- corpo

    @Test
    @DisplayName("o corpo tira o cabecalho e a marcacao do WhatsApp, e preserva o resto")
    void corpoLimpo() {
        String mensagem = """
                ✈️ *Oportunidade encontrada*

                GRU → LIS
                *R$ 2.900,00*
                ⚠️ _Preço não confirmado._

                _Monitor: viagem_de_ferias_""";

        assertThat(PushNotificationChannel.corpo(mensagem)).isEqualTo("""
                GRU → LIS
                R$ 2.900,00
                ⚠️ Preço não confirmado.

                Monitor: viagem_de_ferias""");
    }
}
