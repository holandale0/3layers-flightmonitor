package com.flightmonitor.core.alert.boundary.whatsapp;

import com.flightmonitor.core.alert.boundary.whatsapp.WhatsAppWebhookHandler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe as notificacoes de status da Meta — etapa E1.17.
 *
 * <p>Este e o unico endpoint do sistema que o mundo inteiro pode alcancar: a
 * Meta precisa chegar aqui, entao ele fica exposto. Isso muda o que e razoavel
 * fazer nele.
 *
 * <h2>Tres regras que valem para todo webhook, e nao so para este</h2>
 *
 * <ol>
 *   <li><b>Responder 200 quase sempre.</b> A Meta retenta o que nao recebe 200,
 *       e depois de falhas repetidas <b>desativa</b> o webhook. Um payload que
 *       nao entendemos e menos grave do que perder a assinatura inteira — e foi
 *       ficar sem webhook que escondeu o BUG-007 por horas.</li>
 *   <li><b>Responder rapido.</b> Timeout tambem conta como falha. O trabalho
 *       aqui e curto: achar o alerta pelo {@code wamid} e atualizar.</li>
 *   <li><b>Conferir a assinatura.</b> Sem isso, qualquer um que descubra a URL
 *       pode marcar alertas como entregues, ou como falhos — apagando
 *       justamente o sinal que este endpoint existe para capturar.</li>
 * </ol>
 *
 * <h2>Como cadastrar</h2>
 *
 * <pre>
 * URL:   https://SEU-DOMINIO/api/webhooks/whatsapp
 * Token: o valor de WHATSAPP_WEBHOOK_VERIFY_TOKEN
 * Campo: messages
 * </pre>
 *
 * Em desenvolvimento, um tunel (ngrok, cloudflared) resolve — ver
 * docs/GUIA-WEBHOOK.md.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO_ASSINATURA = "sha256=";

    private final WhatsAppWebhookHandler servico;
    private final WhatsAppProperties props;

    public WhatsAppWebhookController(WhatsAppWebhookHandler servico, WhatsAppProperties props) {
        this.servico = servico;
        this.props = props;
    }

    /**
     * Verificacao inicial, feita uma vez quando voce cadastra a URL no painel.
     *
     * <p>A Meta chama com um desafio e o token que voce cadastrou; a resposta
     * tem que ser o desafio <b>puro</b>, sem aspas e sem JSON em volta. Devolver
     * JSON aqui e o motivo classico de "a Meta nao aceita minha URL".
     */
    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {

        if (props.webhookVerifyToken() == null || props.webhookVerifyToken().isBlank()) {
            log.warn("verificacao de webhook recebida, mas WHATSAPP_WEBHOOK_VERIFY_TOKEN esta vazio");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Comparacao em tempo constante: o token e um segredo, e comparar com
        // equals() vaza o tamanho do prefixo correto por tempo de resposta.
        boolean tokenConfere = MessageDigest.isEqual(
                bytes(props.webhookVerifyToken()), bytes(token));

        if ("subscribe".equals(modo) && tokenConfere) {
            log.info("webhook verificado pela Meta com sucesso");
            return ResponseEntity.ok(desafio);
        }

        log.warn("verificacao de webhook RECUSADA (modo={}, token confere={})", modo, tokenConfere);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Recebe um lote de status.
     *
     * <p>O corpo entra como {@code String} de proposito, e nao como objeto
     * desserializado: a assinatura e calculada sobre os <b>bytes exatos</b> que
     * a Meta enviou. Deixar o Spring desserializar e depois re-serializar para
     * conferir produziria outro texto, e a assinatura nunca bateria.
     */
    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestBody(required = false) String corpo,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {

        if (!assinaturaValida(corpo, assinatura)) {
            // Aqui SIM recusamos: assinatura invalida nao vem da Meta, entao
            // nao ha assinatura a preservar. E o unico 401 deste endpoint.
            log.warn("webhook com assinatura invalida, recusado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (corpo == null || corpo.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            int aplicados = servico.aplicar(corpo);
            if (aplicados > 0) {
                log.info("webhook aplicou {} atualizacao(oes) de status", aplicados);
            }
        } catch (RuntimeException e) {
            // 200 mesmo assim, de proposito. Um bug nosso nao pode custar a
            // assinatura do webhook — perde-lo custaria muito mais do que
            // perder este lote.
            log.error("falha ao aplicar webhook; respondendo 200 para nao perder a assinatura", e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Confere o HMAC-SHA256 do corpo com o segredo do app.
     *
     * <p><b>Sem segredo configurado, aceita.</b> E uma escolha consciente para o
     * desenvolvimento local, registrada com aviso em log a cada requisicao — o
     * incomodo e proposital. Em qualquer ambiente exposto, configure
     * {@code WHATSAPP_APP_SECRET}.
     */
    private boolean assinaturaValida(String corpo, String assinatura) {
        String segredo = props.appSecret();

        if (segredo == null || segredo.isBlank()) {
            log.warn("WHATSAPP_APP_SECRET vazio: aceitando webhook SEM conferir assinatura");
            return true;
        }

        if (assinatura == null || !assinatura.startsWith(PREFIXO_ASSINATURA)) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(bytes(segredo), ALGORITMO));
            byte[] esperado = mac.doFinal(bytes(corpo));

            byte[] recebido = HexFormat.of()
                    .parseHex(assinatura.substring(PREFIXO_ASSINATURA.length()).trim());

            // MessageDigest.isEqual e a comparacao em tempo constante da JDK.
            // Arrays.equals para aqui na primeira diferenca, e a diferenca de
            // tempo permite descobrir a assinatura byte a byte.
            return MessageDigest.isEqual(esperado, recebido);
        } catch (IllegalArgumentException e) {
            // Hexadecimal malformado no cabecalho.
            return false;
        } catch (Exception e) {
            log.error("falha ao conferir assinatura do webhook", e);
            return false;
        }
    }

    private static byte[] bytes(String valor) {
        return valor == null ? new byte[0] : valor.getBytes(StandardCharsets.UTF_8);
    }
}
