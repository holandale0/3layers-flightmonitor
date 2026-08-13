package com.flightmonitor.core.alert.control.dto;

import java.time.Instant;

/**
 * O que a tela mostra sobre o canal WhatsApp — etapa E4.7.
 *
 * @param origem {@code BANCO} ou {@code AMBIENTE}. A tela precisa disso para
 *        dizer ao usuario se ele esta vendo algo que pode editar ou algo que
 *        veio do {@code .env} — sem isso, salvar pareceria nao ter efeito
 * @param tokenConfigurado se ha token no ambiente. <b>Booleano, nunca o valor:</b>
 *        um endpoint que devolve segredo transforma qualquer leitura em
 *        vazamento, e a tela nao precisa do token para dizer o que falta
 * @param pronto se da para enviar: token no ambiente <b>e</b> numero conhecido
 */
public record WhatsAppConfigResponse(
        String phoneNumberId,
        String wabaId,
        String templateName,
        String templateLanguage,
        String origem,
        boolean tokenConfigurado,
        boolean pronto,
        Instant updatedAt) {
}
