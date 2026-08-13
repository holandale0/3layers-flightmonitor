package com.flightmonitor.core.recipient.control.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro ou atualizacao de um destinatario de alertas.
 *
 * <p>O telefone e normalizado antes de validar: espacos, hifens, parenteses e
 * pontos sao removidos. O "+" do E.164 nao e inventado — se faltar, o cadastro
 * e recusado, porque adivinhar o pais seria pior do que reclamar.
 *
 * <p><b>Pelo menos um contato</b> (E4.6): telefone, e-mail, ou os dois. Ate a
 * E4.6 o telefone era obrigatorio porque o WhatsApp era o unico canal; exigi-lo
 * de quem so recebe por e-mail produziria um numero inventado — que um dia
 * receberia mensagem de verdade. A regra esta em {@link ContatoAlcancavel},
 * porque nenhuma anotacao de campo sozinha consegue dizer "um OU outro".
 */
@ContatoAlcancavel
public record RecipientRequest(

        @NotBlank(message = "obrigatorio")
        @Size(max = 120, message = "no maximo 120 caracteres")
        String name,

        @Pattern(
                regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "use o formato E.164, com codigo do pais: +5511999998888")
        String phoneE164,

        // @Email do Jakarta e deliberadamente frouxo, e isso e desejavel: regex
        // estrita de e-mail recusa endereco valido, e o custo desse erro e uma
        // pessoa que nao consegue se cadastrar. O CHECK do banco e igualmente
        // frouxo, pelo mesmo motivo.
        @Email(message = "endereco de e-mail invalido")
        @Size(max = 254, message = "no maximo 254 caracteres")
        String email,

        Boolean active) {

    public RecipientRequest {
        if (name != null) {
            name = name.trim();
        }
        if (phoneE164 != null) {
            phoneE164 = phoneE164.replaceAll("[\\s()\\-.]", "");
        }
        if (email != null) {
            // Minusculas porque o dominio e insensivel a caixa e a parte local,
            // na pratica, tambem: sem isso, "Leo@x.com" e "leo@x.com" passariam
            // pelo indice unico como duas pessoas e a caixa receberia dobrado.
            email = email.trim().toLowerCase();
        }
        // Campo vazio e ausencia de campo sao a mesma coisa aqui. Sem isto, uma
        // string vazia vinda de formulario passaria pelo "tem algum contato" e
        // viraria um contato que nao alcanca ninguem.
        phoneE164 = vazioViraNulo(phoneE164);
        email = vazioViraNulo(email);

        if (active == null) {
            active = Boolean.TRUE;
        }
    }

    private static String vazioViraNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
