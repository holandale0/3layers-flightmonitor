package com.flightmonitor.core.alert.boundary.whatsapp;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.control.AlertMessageFormatter;
import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.control.NotificationChannel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Entrega alertas pelo WhatsApp Cloud API.
 *
 * <p><b>Envia template, e nao texto livre.</b> Alerta e mensagem iniciada pela
 * empresa, e a Meta so aceita template aprovado nesse caso — texto livre vale
 * apenas dentro da janela de 24h depois de o destinatario escrever. Um monitor
 * que avisa de madrugada nunca esta nessa janela. Ver docs/GUIA-WHATSAPP.md.
 *
 * <p>Nunca lanca excecao: devolve {@link DeliveryResult} classificando a falha,
 * porque quem decide se vale retentar e o despachante.
 */
@Component
public class WhatsAppNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationChannel.class);

    private final WhatsAppProperties props;
    private final ConfiguracaoDoWhatsApp configuracao;
    private final AlertMessageFormatter formatador;
    private final ObjectMapper json;
    private final RestClient client;

    public WhatsAppNotificationChannel(
            WhatsAppProperties props,
            ConfiguracaoDoWhatsApp configuracao,
            AlertMessageFormatter formatador,
            ObjectMapper json) {
        this.props = props;
        this.configuracao = configuracao;
        this.formatador = formatador;
        this.json = json;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                // Versao declarada, e nao herdada do padrao da JDK. Licao do
                // BUG-004: o default HTTP/2 causou falha intermitente contra um
                // servidor 1.1, e o sintoma parecia problema de rede.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.timeout());

        this.client = RestClient.builder().requestFactory(factory).build();

        if (!props.temToken()) {
            log.warn("WhatsApp sem token: defina WHATSAPP_ACCESS_TOKEN no .env antes de "
                    + "ativar este canal. O numero e o template podem vir da tela (E4.7), "
                    + "mas o token nunca — segredo nao mora em banco");
        }
    }

    @Override
    public AlertChannel canal() {
        return AlertChannel.WHATSAPP;
    }

    /**
     * Sim: o {@code wamid} e numero de protocolo, nao comprovante.
     *
     * <p>Quem fecha a duvida e o {@link WhatsAppWebhookController}. Sem ele
     * ligado, os alertas param em ACCEPTED — o que e desconfortavel, e honesto:
     * nao sabemos mesmo. Ver D-053 e BUG-007.
     */
    @Override
    public boolean confirmacaoAssincrona() {
        return true;
    }

    @Override
    public DeliveryResult enviar(Alert alerta) {
        // Lida AQUI, e nao no construtor: e o que permite trocar template ou
        // numero pela tela sem reiniciar (E4.7).
        ConfiguracaoDoWhatsApp.Efetiva efetiva = configuracao.atual();

        if (!props.temToken()) {
            return DeliveryResult.falhaPermanente(
                    "WhatsApp sem WHATSAPP_ACCESS_TOKEN no ambiente: veja docs/GUIA-WHATSAPP.md");
        }
        if (!efetiva.identificada()) {
            return DeliveryResult.falhaPermanente(
                    "WhatsApp sem numero remetente: configure em /configuracao ou "
                            + "defina WHATSAPP_PHONE_NUMBER_ID no .env");
        }
        if (alerta.getRecipient() == null) {
            return DeliveryResult.falhaPermanente("alerta sem destinatario");
        }
        if (alerta.getMonitor() == null || alerta.getPriceObservation() == null) {
            // Pode acontecer se o monitor foi apagado: as FKs sao ON DELETE SET
            // NULL para preservar o historico (D-016). Sem eles nao ha como
            // montar os parametros do template.
            return DeliveryResult.falhaPermanente(
                    "alerta sem monitor ou observacao: nao da para montar o template");
        }

        // A analise vem do proprio alerta, gravada quando ele foi criado (E2.4).
        // Recalcular aqui exigiria ir ao banco de dentro do adaptador, fora de
        // transacao e com a entidade desanexada.
        List<String> parametros = formatador.parametrosDoTemplate(
                alerta.getMonitor(),
                alerta.getPriceObservation(),
                com.flightmonitor.core.alert.control.AlertInsights.doAlerta(alerta));

        try {
            String corpo = client.post()
                    .uri(props.enderecoDeEnvio(efetiva.phoneNumberId()))
                    .header("Authorization", "Bearer " + props.accessToken())
                    .header("Content-Type", "application/json")
                    .body(montarPayload(alerta, parametros, efetiva))
                    .retrieve()
                    .body(String.class);

            return extrairId(corpo);

        } catch (RestClientResponseException e) {
            return classificarRespostaDeErro(e);
        } catch (RestClientException e) {
            // Timeout, conexao recusada, DNS. Vale tentar de novo.
            return DeliveryResult.falhaTransitoria("falha de rede: " + e.getMessage());
        }
    }

    /**
     * Monta o corpo do template.
     *
     * <p>O telefone vai <b>sem</b> o "+" do E.164: a Graph API espera so digitos.
     */
    private Map<String, Object> montarPayload(
            Alert alerta, List<String> parametros, ConfiguracaoDoWhatsApp.Efetiva efetiva) {
        List<Map<String, String>> componentes = parametros.stream()
                .map(valor -> Map.of("type", "text", "text", valor))
                .toList();

        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", alerta.getRecipient().getPhoneE164().replace("+", ""),
                "type", "template",
                "template", Map.of(
                        "name", efetiva.templateName(),
                        "language", Map.of("code", efetiva.templateLanguage()),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", componentes))));
    }

    private DeliveryResult extrairId(String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            JsonNode mensagens = raiz.path("messages");
            if (mensagens.isArray() && !mensagens.isEmpty()) {
                String id = mensagens.get(0).path("id").asString();
                if (id != null && !id.isBlank()) {
                    return DeliveryResult.entregue(id);
                }
            }
            // HTTP 200 sem id e resposta fora do contrato. Nao inventamos
            // sucesso: sem id nao ha como rastrear a entrega depois.
            return DeliveryResult.falhaTransitoria("resposta 200 sem id de mensagem");
        } catch (RuntimeException e) {
            return DeliveryResult.falhaTransitoria("resposta ilegivel: " + e.getMessage());
        }
    }

    private DeliveryResult classificarRespostaDeErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        int codigo = 0;
        String mensagemDaMeta = null;

        try {
            JsonNode erro = json.readTree(e.getResponseBodyAsString()).path("error");
            codigo = erro.path("code").asInt(0);
            mensagemDaMeta = erro.path("message").asString(null);
        } catch (RuntimeException ignorado) {
            // Corpo nao e o JSON de erro esperado; o status HTTP decide sozinho.
        }

        String explicacao = MetaErro.explicar(codigo);
        String detalhe = "HTTP %d, codigo %d: %s".formatted(
                status, codigo, explicacao != null ? explicacao : mensagemDaMeta);

        if (MetaErro.transitorio(codigo, status)) {
            log.warn("WhatsApp devolveu erro transitorio — {}", detalhe);
            return DeliveryResult.falhaTransitoria(detalhe);
        }

        log.error("WhatsApp devolveu erro permanente — {}", detalhe);
        return DeliveryResult.falhaPermanente(detalhe);
    }
}
