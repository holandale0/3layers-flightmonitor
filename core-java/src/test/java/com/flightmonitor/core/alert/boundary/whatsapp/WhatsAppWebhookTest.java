package com.flightmonitor.core.alert.boundary.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.AlertStatus;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;

/**
 * Webhook de status do WhatsApp — etapa E1.17.
 *
 * <p>Este e o teste que faltava existir no dia do BUG-007. As quatro mensagens
 * que "foram enviadas" e nunca chegaram tinham a explicacao no webhook — codigo
 * 130497 — e o sistema nao tinha onde recebe-lo. O
 * {@link #falhaDeEntregaVeioComOMotivoDoBug007()} reproduz aquele payload exato.
 *
 * <p>O segredo do app fica configurado aqui de proposito: sem ele o controller
 * aceita qualquer POST, e um teste que rodasse nesse modo nao provaria a parte
 * mais importante — que a assinatura e conferida.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "flightmonitor.whatsapp.webhook-verify-token=token-combinado-com-a-meta",
        "flightmonitor.whatsapp.app-secret=segredo-do-app-de-teste",
})
class WhatsAppWebhookTest {

    private static final String SEGREDO = "segredo-do-app-de-teste";
    private static final String WAMID = "wamid.HBgNNTUxMTkyMDcxNDE3MBUCABEYEjcxRDVBNjcyQjVGMkE0MDBFQQA=";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private AlertRepository alertas;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

    @Autowired
    private PriceObservationRepository observacoes;

    private MockMvc mvc;
    private Alert alerta;

    @BeforeEach
    void preparar() {
        limparBanco();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        Recipient r = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511999990000"));

        Monitor m = new Monitor();
        m.setLabel("webhook");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(LocalDate.now().plusMonths(3));
        m.setDepartureWindowEnd(LocalDate.now().plusMonths(3).plusDays(10));
        m.setMaxPrice(new BigDecimal("4000.00"));
        m.addRecipient(r);
        m = monitores.saveAndFlush(m);

        PriceObservation o = observacoes.saveAndFlush(new PriceObservation(
                m, "GRU", "LIS", LocalDate.now().plusMonths(3),
                new BigDecimal("3720.00"), PriceSource.FAST_FLIGHTS));

        Alert a = new Alert(m, o, r, "mensagem de teste");
        a.setChannel(AlertChannel.WHATSAPP);
        // Estado em que o adaptador do WhatsApp deixa um alerta despachado: a
        // Meta aceitou e devolveu wamid, e nao sabemos se chegou.
        a.marcarAceito(WAMID);
        alerta = alertas.saveAndFlush(a);
    }

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        alertas.deleteAll();
        observacoes.deleteAll();
        monitores.deleteAll();
        destinatarios.deleteAll();
    }

    private Alert recarregar() {
        return alertas.findById(alerta.getId()).orElseThrow();
    }

    // ---------------------------------------------------------- assinatura

    private String assinar(String corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of()
                    .formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void enviar(String corpo) throws Exception {
        mvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", assinar(corpo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- payloads

    private String statusSimples(String situacao, long unixSegundos) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "999999999999999",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "5511920714170",
                          "phone_number_id": "888888888888888"
                        },
                        "statuses": [{
                          "id": "%s",
                          "status": "%s",
                          "timestamp": "%d",
                          "recipient_id": "5511999990000"
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(WAMID, situacao, unixSegundos);
    }

    private long agoraEmSegundos() {
        return Instant.now().plusSeconds(2).getEpochSecond();
    }

    // ------------------------------------------------------- verificacao

    @Test
    @DisplayName("verificacao devolve o desafio puro, sem JSON em volta")
    void verificacaoDevolveODesafio() throws Exception {
        mvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-combinado-com-a-meta")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                // Puro: com aspas ou envelope JSON, a Meta recusa o cadastro.
                .andExpect(content().string("1158201444"));
    }

    @Test
    @DisplayName("token errado na verificacao e recusado")
    void verificacaoComTokenErrado() throws Exception {
        mvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "chute")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("modo diferente de subscribe e recusado mesmo com o token certo")
    void verificacaoComModoErrado() throws Exception {
        mvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", "token-combinado-com-a-meta")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------- autenticacao

    @Test
    @DisplayName("sem assinatura, o POST e recusado")
    void semAssinaturaRecusa() throws Exception {
        mvc.perform(post("/api/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusSimples("delivered", agoraEmSegundos())))
                .andExpect(status().isUnauthorized());

        assertThat(recarregar().getStatus()).isEqualTo(AlertStatus.ACCEPTED);
    }

    @Test
    @DisplayName("assinatura de outro segredo e recusada")
    void assinaturaForjadaRecusa() throws Exception {
        String corpo = statusSimples("delivered", agoraEmSegundos());

        mvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", "sha256=" + "00".repeat(32))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnauthorized());

        // O ponto desta defesa: sem ela, qualquer um marcaria um alerta como
        // entregue — apagando o sinal que este endpoint existe para capturar.
        assertThat(recarregar().getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("assinatura com hexadecimal malformado e recusada, nao explode")
    void assinaturaMalformadaRecusa() throws Exception {
        mvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", "sha256=nao-e-hexadecimal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusSimples("delivered", agoraEmSegundos())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("assinatura sobre o corpo alterado nao bate")
    void corpoAlteradoNaoBate() throws Exception {
        String original = statusSimples("delivered", agoraEmSegundos());
        String alterado = original.replace("delivered", "failed");

        mvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", assinar(original))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alterado))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------- transicoes

    @Test
    @DisplayName("delivered transforma ACCEPTED em SENT — e so agora SENT significa entregue")
    void entregaConfirmada() throws Exception {
        long quando = agoraEmSegundos();

        enviar(statusSimples("delivered", quando));

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(depois.getDeliveredAt()).isEqualTo(Instant.ofEpochSecond(quando));
        assertThat(depois.getReadAt()).isNull();
        assertThat(depois.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("read registra a leitura sem apagar a entrega")
    void leituraRegistrada() throws Exception {
        long entrega = agoraEmSegundos();
        enviar(statusSimples("delivered", entrega));
        enviar(statusSimples("read", entrega + 30));

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(depois.getDeliveredAt()).isEqualTo(Instant.ofEpochSecond(entrega));
        assertThat(depois.getReadAt()).isEqualTo(Instant.ofEpochSecond(entrega + 30));
    }

    @Test
    @DisplayName("read antes de delivered tambem confirma a entrega")
    void leituraForaDeOrdem() throws Exception {
        // A Meta nao garante ordem. Se a leitura chegasse primeiro e nao
        // confirmasse a entrega, o alerta ficaria "lido mas nao entregue".
        long quando = agoraEmSegundos();

        enviar(statusSimples("read", quando));

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(depois.getReadAt()).isEqualTo(Instant.ofEpochSecond(quando));
        assertThat(depois.getDeliveredAt()).isEqualTo(Instant.ofEpochSecond(quando));
    }

    @Test
    @DisplayName("delivered atrasado nao apaga a leitura ja registrada")
    void entregaAtrasadaNaoApagaLeitura() throws Exception {
        long quando = agoraEmSegundos();
        enviar(statusSimples("read", quando + 30));
        enviar(statusSimples("delivered", quando));

        Alert depois = recarregar();
        assertThat(depois.getReadAt()).isEqualTo(Instant.ofEpochSecond(quando + 30));
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
    }

    @Test
    @DisplayName("'sent' nao muda nada: e a Meta repetindo o que o wamid ja disse")
    void statusSentNaoAcrescenta() throws Exception {
        enviar(statusSimples("sent", agoraEmSegundos()));

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.ACCEPTED);
        assertThat(depois.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("o mesmo aviso duas vezes nao reescreve a historia")
    void avisoRepetidoEIdempotente() throws Exception {
        long primeira = agoraEmSegundos();
        enviar(statusSimples("delivered", primeira));
        enviar(statusSimples("delivered", primeira + 600));

        // A Meta reenvia o lote quando nao recebe 200 rapido. O horario de
        // entrega e o da PRIMEIRA confirmacao, nao o da repeticao.
        assertThat(recarregar().getDeliveredAt()).isEqualTo(Instant.ofEpochSecond(primeira));
    }

    // -------------------------------------------------------------- falha

    @Test
    @DisplayName("BUG-007: a falha que estava invisivel agora vira error_message")
    void falhaDeEntregaVeioComOMotivoDoBug007() throws Exception {
        // Payload reproduzido do webhook real que revelou o BUG-007.
        String corpo = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "777777777777777",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "statuses": [{
                          "id": "%s",
                          "status": "failed",
                          "timestamp": "%d",
                          "recipient_id": "5511999990000",
                          "errors": [{
                            "code": 130497,
                            "title": "Message Undeliverable",
                            "message": "Message Undeliverable",
                            "error_data": {
                              "details": "Business account is restricted from messaging users in this country."
                            }
                          }]
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(WAMID, agoraEmSegundos());

        enviar(corpo);

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(depois.getDeliveredAt()).isNull();
        assertThat(depois.getErrorMessage())
                .contains("130497")
                // A nossa explicacao, para quem le o banco sem conhecer a Meta.
                .contains("proibida de enviar para este pais")
                // E o texto original, que e o que se pesquisa na documentacao.
                .contains("Business account is restricted");
    }

    @Test
    @DisplayName("falha sem detalhe ainda registra que falhou")
    void falhaSemDetalhe() throws Exception {
        String corpo = statusSimples("failed", agoraEmSegundos());

        enviar(corpo);

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(depois.getErrorMessage()).contains("sem detalhar o motivo");
    }

    @Test
    @DisplayName("falha depois de entrega confirmada nao desfaz a entrega")
    void falhaDepoisDaEntregaNaoDesfaz() throws Exception {
        long quando = agoraEmSegundos();
        enviar(statusSimples("delivered", quando));
        enviar(statusSimples("failed", quando + 5));

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(depois.getErrorMessage()).isNull();
    }

    // --------------------------------------------------------- robustez

    @Test
    @DisplayName("wamid desconhecido responde 200 e ignora")
    void wamidDesconhecido() throws Exception {
        String corpo = statusSimples("delivered", agoraEmSegundos())
                .replace(WAMID, "wamid.mensagem-que-nao-e-nossa");

        enviar(corpo);

        // A conta pode ter mensagens que este sistema nao enviou. Recusar faria
        // a Meta retentar e, com repeticao, desativar o webhook.
        assertThat(recarregar().getStatus()).isEqualTo(AlertStatus.ACCEPTED);
    }

    @Test
    @DisplayName("corpo que nao e JSON responde 200 em vez de derrubar a assinatura")
    void corpoInvalidoNaoDerruba() throws Exception {
        enviar("isto nao e json");

        assertThat(recarregar().getStatus()).isEqualTo(AlertStatus.ACCEPTED);
    }

    @Test
    @DisplayName("notificacao sem 'statuses' — mensagem recebida, por exemplo — e ignorada")
    void notificacaoDeOutroTipo() throws Exception {
        // A assinatura do campo "messages" tambem entrega mensagens que o
        // usuario manda. Nao e status, e nao pode virar erro.
        enviar("""
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{
                  "field":"messages","value":{"messaging_product":"whatsapp","messages":[
                    {"from":"5511999990000","id":"wamid.entrada","type":"text",
                     "text":{"body":"ola"}}]}}]}]}
                """);

        assertThat(recarregar().getStatus()).isEqualTo(AlertStatus.ACCEPTED);
    }

    @Test
    @DisplayName("lote com varios status aplica todos, e um desconhecido nao atrapalha")
    void loteComVariosStatus() throws Exception {
        long quando = agoraEmSegundos();
        String corpo = """
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{
                  "field":"messages","value":{"messaging_product":"whatsapp","statuses":[
                    {"id":"wamid.de-outra-pessoa","status":"delivered","timestamp":"%d"},
                    {"id":"%s","status":"delivered","timestamp":"%d"},
                    {"id":"%s","status":"read","timestamp":"%d"}
                  ]}}]}]}
                """.formatted(quando, WAMID, quando, WAMID, quando + 10);

        enviar(corpo);

        Alert depois = recarregar();
        assertThat(depois.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(depois.getDeliveredAt()).isEqualTo(Instant.ofEpochSecond(quando));
        assertThat(depois.getReadAt()).isEqualTo(Instant.ofEpochSecond(quando + 10));
    }

    @Test
    @DisplayName("timestamp e lido em SEGUNDOS, nao em milissegundos")
    void timestampEmSegundos() throws Exception {
        long quando = agoraEmSegundos();

        enviar(statusSimples("delivered", quando));

        // Interpretar como milissegundos jogaria a data para 1970 — e o CHECK
        // de coerencia do banco recusaria a linha.
        assertThat(recarregar().getDeliveredAt())
                .isAfter(Instant.now().minusSeconds(60))
                .isBefore(Instant.now().plusSeconds(3600));
    }
}
