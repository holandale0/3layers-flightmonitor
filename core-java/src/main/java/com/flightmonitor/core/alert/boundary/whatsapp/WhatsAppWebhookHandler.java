package com.flightmonitor.core.alert.boundary.whatsapp;


import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduz as notificacoes de status da Meta em fatos sobre os nossos alertas.
 *
 * <p><b>Handler, e nao Service:</b> o nome antigo vinha do endpoint que o chama,
 * e escondia o papel. Isto aqui e borda — existe para converter o formato de um
 * sistema externo em chamadas sobre as nossas entidades. O teste de arquitetura
 * cobrou a diferenca quando o controle passou a depender do mapa de erros da
 * Meta, que e conhecimento da borda.
 *
 * <p>Esta classe existe por causa do BUG-007: quatro mensagens foram aceitas
 * com {@code wamid}, marcadas como enviadas, e <b>nunca chegaram</b>. O motivo —
 * codigo 130497, conta restrita de enviar para o Brasil — estava no webhook o
 * tempo todo. Ninguem estava lendo.
 *
 * <h2>Formato do que chega</h2>
 *
 * <pre>
 * entry[].changes[].value.statuses[] = {
 *     id:        "wamid.HBg..."     &lt;- o elo com o nosso alerta
 *     status:    sent | delivered | read | failed
 *     timestamp: "1786...."         &lt;- unix em SEGUNDOS, como texto
 *     errors:    [{ code, title, message, error_data.details }]
 * }
 * </pre>
 *
 * <h2>Tres coisas que o formato impoe</h2>
 *
 * <ol>
 *   <li><b>Ordem nao e garantida.</b> {@code read} pode chegar antes de
 *       {@code delivered}. As transicoes em {@link Alert} tratam disso.</li>
 *   <li><b>Repeticao e esperada.</b> A Meta reenvia o lote inteiro se nao
 *       receber 200 rapido. Toda aplicacao aqui e idempotente.</li>
 *   <li><b>Um lote traz varios status</b>, de mensagens diferentes. Um
 *       identificador desconhecido nao pode derrubar os outros — a conta pode
 *       ter mensagens que este sistema nao enviou.</li>
 * </ol>
 */
@Service
public class WhatsAppWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookHandler.class);

    private final AlertRepository alertas;
    private final ObjectMapper json;

    public WhatsAppWebhookHandler(AlertRepository alertas, ObjectMapper json) {
        this.alertas = alertas;
        this.json = json;
    }

    /**
     * Aplica todos os status de um lote.
     *
     * @return quantos alertas mudaram de fato
     */
    @Transactional
    public int aplicar(String corpo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(corpo);
        } catch (RuntimeException e) {
            // Devolver erro faria a Meta retentar o mesmo corpo invalido, e
            // depois de varias falhas ela DESATIVA o webhook. Registrar e
            // seguir e menos ruim.
            log.warn("webhook com corpo ilegivel, ignorando: {}", e.getMessage());
            return 0;
        }

        int aplicados = 0;

        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode mudanca : entrada.path("changes")) {
                for (JsonNode status : mudanca.path("value").path("statuses")) {
                    if (aplicarUm(status)) {
                        aplicados++;
                    }
                }
            }
        }

        return aplicados;
    }

    private boolean aplicarUm(JsonNode status) {
        String wamid = texto(status, "id");
        String situacao = texto(status, "status");

        if (wamid == null || situacao == null) {
            log.debug("status sem id ou sem situacao, ignorando");
            return false;
        }

        Optional<Alert> encontrado = alertas.findByProviderMessageId(wamid);
        if (encontrado.isEmpty()) {
            // Nao e erro: a conta do WhatsApp pode ter mensagens que este
            // sistema nao enviou, e a Meta notifica todas.
            log.debug("status '{}' para mensagem desconhecida {}", situacao, resumir(wamid));
            return false;
        }

        Alert alerta = encontrado.get();
        Instant quando = instante(status);

        boolean mudou = switch (situacao) {
            case "delivered" -> alerta.marcarEntregue(quando);
            case "read" -> alerta.marcarLido(quando);
            case "failed" -> alerta.marcarFalhaDeEntrega(motivoDaFalha(status));
            // "sent" e a Meta confirmando que ela despachou — exatamente o que
            // ja sabiamos ao receber o wamid. Nao acrescenta nada.
            case "sent" -> false;
            default -> {
                log.info("status desconhecido '{}' para o alerta {}", situacao, alerta.getId());
                yield false;
            }
        };

        if (mudou) {
            alertas.saveAndFlush(alerta);
            if ("failed".equals(situacao)) {
                log.error("alerta {} NAO foi entregue: {}", alerta.getId(), alerta.getErrorMessage());
            } else {
                log.info("alerta {} -> {} ({})", alerta.getId(), alerta.getStatus(), situacao);
            }
        }

        return mudou;
    }

    /**
     * Monta a explicacao da falha com o que a Meta mandar.
     *
     * <p>O campo {@code error_data.details} costuma ser o mais util: no BUG-007
     * era ele que dizia <i>"Business account is restricted from messaging users
     * in this country"</i>, enquanto o {@code title} trazia apenas o generico
     * "Message Undeliverable".
     */
    private String motivoDaFalha(JsonNode status) {
        JsonNode erros = status.path("errors");
        if (!erros.isArray() || erros.isEmpty()) {
            return "a Meta informou falha de entrega, sem detalhar o motivo";
        }

        JsonNode erro = erros.get(0);
        int codigo = erro.path("code").asInt(0);
        String titulo = texto(erro, "title");
        String detalhe = texto(erro.path("error_data"), "details");
        if (detalhe == null) {
            detalhe = texto(erro, "message");
        }

        StringBuilder sb = new StringBuilder("entrega recusada");
        if (codigo != 0) {
            sb.append(" (codigo ").append(codigo).append(')');
        }

        // A explicacao em portugues so existe para o que ja reconhecemos. Para o
        // resto, o texto da propria Meta e melhor do que um generico nosso.
        String legivel = MetaErro.explicar(codigo);
        if (legivel != null) {
            sb.append(": ").append(legivel);
        }
        if (titulo != null) {
            sb.append(" | ").append(titulo);
        }
        if (detalhe != null) {
            sb.append(" | ").append(detalhe);
        }
        return sb.toString();
    }

    /**
     * O timestamp vem em segundos, como texto.
     *
     * <p>Interpretar como milissegundos jogaria a data para 1970 e faria o
     * CHECK de coerencia do banco recusar a linha.
     */
    private Instant instante(JsonNode status) {
        String bruto = texto(status, "timestamp");
        if (bruto == null) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(bruto.trim()));
        } catch (NumberFormatException e) {
            log.debug("timestamp ilegivel '{}', usando o momento atual", bruto);
            return Instant.now();
        }
    }

    private String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String s = valor.asString();
        return s == null || s.isBlank() ? null : s;
    }

    /** Os wamid sao longos e nao dizem nada; o log fica legivel com o comeco. */
    private String resumir(String wamid) {
        return wamid.length() <= 24 ? wamid : wamid.substring(0, 24) + "...";
    }
}
