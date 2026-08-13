package com.flightmonitor.core.alert.boundary.whatsapp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e ajustes do WhatsApp Cloud API.
 *
 * @param phoneNumberId identificador do numero remetente. E um numero longo,
 *        <b>nao</b> o telefone — confundir os dois e o erro mais comum
 * @param accessToken token do usuario de sistema. O token temporario do painel
 *        dura 24h e faria o monitor parar de avisar toda madrugada
 * @param baseUrl endereco da Graph API. Configuravel para os testes apontarem
 *        para um servidor falso
 * @param templateName template aprovado na Meta. Alerta e mensagem iniciada
 *        pela empresa, e essas exigem template — texto livre so vale dentro da
 *        janela de 24h. Ver docs/GUIA-WHATSAPP.md
 * @param webhookVerifyToken segredo que voce inventa e informa a Meta ao
 *        cadastrar o webhook. Ela o devolve na verificacao inicial; se nao
 *        bater, o cadastro e recusado
 * @param appSecret segredo do app, usado para conferir a assinatura
 *        {@code X-Hub-Signature-256} de cada notificacao. <b>Sem ele o endpoint
 *        aceita qualquer POST</b> — e ele e publico por natureza, porque a Meta
 *        precisa alcanca-lo
 */
@ConfigurationProperties(prefix = "flightmonitor.whatsapp")
public record WhatsAppProperties(
        String phoneNumberId,
        String accessToken,
        String baseUrl,
        String apiVersion,
        String templateName,
        String templateLanguage,
        String webhookVerifyToken,
        String appSecret,
        Duration timeout) {

    public WhatsAppProperties {
        baseUrl = vazio(baseUrl) ? "https://graph.facebook.com" : baseUrl;
        apiVersion = vazio(apiVersion) ? "v21.0" : apiVersion;
        // O nome mudou de "alerta_passagem" para este: a Meta classificou o
        // primeiro como MARKETING por causa de uma frase promocional no corpo,
        // e depois de apagado ela bloqueia recriar o MESMO nome com categoria
        // diferente. Nome novo foi o desbloqueio. Ver docs/GUIA-WHATSAPP.md.
        templateName = vazio(templateName) ? "alerta_preco_voo" : templateName;
        templateLanguage = vazio(templateLanguage) ? "pt_BR" : templateLanguage;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
    }

    /**
     * Ha token no ambiente?
     *
     * <p>Separado de "esta configurado" desde a E4.7: o <b>numero</b> pode vir do
     * banco, mas o <b>token</b> so vem daqui. Juntar as duas perguntas fazia o
     * canal recusar envio por falta de numero no ambiente mesmo quando havia
     * numero salvo na tela.
     */
    public boolean temToken() {
        return !vazio(accessToken);
    }

    /**
     * Endereco de envio: {baseUrl}/{versao}/{phoneNumberId}/messages
     *
     * <p>Recebe o identificador em vez de ler o proprio campo: desde a E4.7 quem
     * decide o numero e {@code ConfiguracaoDoWhatsApp}, que consulta o banco
     * antes do ambiente.
     */
    public String enderecoDeEnvio(String phoneNumberId) {
        return "%s/%s/%s/messages".formatted(baseUrl, apiVersion, phoneNumberId);
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }
}
