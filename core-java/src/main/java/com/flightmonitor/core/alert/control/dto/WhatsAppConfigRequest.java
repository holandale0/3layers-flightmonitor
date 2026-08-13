package com.flightmonitor.core.alert.control.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edicao da configuracao nao-secreta do WhatsApp — etapa E4.7.
 *
 * <p><b>Nao ha campo de token, e isso e proposital.</b> Segredo nao entra por
 * formulario: ele viveria no banco, apareceria em backup, e a tela que o grava
 * tornaria autenticacao obrigatoria num projeto que decidiu nao ter login.
 * {@code WHATSAPP_ACCESS_TOKEN} continua vindo do ambiente.
 *
 * @param phoneNumberId identificador Meta do numero remetente. Vazio devolve o
 *        controle ao {@code .env} — e por isso ele nao e obrigatorio
 * @param templateName minusculas, digitos e sublinhado: e o que a Meta aceita.
 *        Recusar aqui evita descobrir o erro so no primeiro alerta de verdade
 * @param templateLanguage codigo da Meta, com <b>sublinhado</b>: {@code pt_BR},
 *        nao {@code pt-BR}. Errar isso devolve 132001 — o mesmo erro de
 *        "template nao existe", o que torna o engano caro de diagnosticar
 */
public record WhatsAppConfigRequest(

        @Size(max = 40, message = "no maximo 40 caracteres")
        @Pattern(regexp = "^[0-9]*$", message = "e' um identificador numerico da Meta, nao um telefone")
        String phoneNumberId,

        @Size(max = 40, message = "no maximo 40 caracteres")
        @Pattern(regexp = "^[0-9]*$", message = "e' um identificador numerico da Meta")
        String wabaId,

        @Pattern(
                regexp = "^[a-z0-9_]+$",
                message = "use minusculas, digitos e sublinhado: alerta_preco_voo")
        @Size(max = 120, message = "no maximo 120 caracteres")
        String templateName,

        @Pattern(
                regexp = "^[a-z]{2}(_[A-Z]{2})?$",
                message = "use o formato da Meta, com sublinhado: pt_BR")
        String templateLanguage) {

    public WhatsAppConfigRequest {
        phoneNumberId = limpar(phoneNumberId);
        wabaId = limpar(wabaId);
        templateName = limpar(templateName);
        templateLanguage = limpar(templateLanguage);

        // Sem valor, valem os padroes — os mesmos do banco. Deixar nulo faria a
        // coluna NOT NULL estourar como erro de constraint no lugar de uma
        // mensagem util.
        if (templateName == null) {
            templateName = "alerta_preco_voo";
        }
        if (templateLanguage == null) {
            templateLanguage = "pt_BR";
        }
    }

    /** Vazio vira nulo: campo em branco significa "volta a valer o ambiente". */
    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
