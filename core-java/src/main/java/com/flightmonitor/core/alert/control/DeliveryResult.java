package com.flightmonitor.core.alert.control;

/**
 * Resultado de uma tentativa de entrega.
 *
 * <p>A distincao entre falha <b>transitoria</b> e <b>permanente</b> e o que
 * torna a retentativa util em vez de teimosa:
 *
 * <ul>
 *   <li><b>transitoria</b> — rede instavel, HTTP 500 do provedor, timeout.
 *       Tentar de novo faz sentido;</li>
 *   <li><b>permanente</b> — numero invalido, mensagem recusada por politica.
 *       Tentar de novo so gasta tentativa e adia o diagnostico.</li>
 * </ul>
 *
 * @param providerMessageId identificador devolvido pelo provedor, para rastrear
 *        a entrega depois. Nulo quando a entrega falhou
 */
public record DeliveryResult(
        boolean sucesso,
        boolean transitorio,
        String providerMessageId,
        String erro) {

    public static DeliveryResult entregue(String providerMessageId) {
        return new DeliveryResult(true, false, providerMessageId, null);
    }

    /** Falhou, mas vale tentar de novo. */
    public static DeliveryResult falhaTransitoria(String erro) {
        return new DeliveryResult(false, true, null, erro);
    }

    /** Falhou e tentar de novo nao vai adiantar. */
    public static DeliveryResult falhaPermanente(String erro) {
        return new DeliveryResult(false, false, null, erro);
    }
}
