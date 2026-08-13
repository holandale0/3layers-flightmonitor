package com.flightmonitor.core.search.control.client.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Rastro de uma tentativa da cadeia de confirmacao.
 *
 * <p>E o que torna a queda de um provider um dado observavel, e nao apenas a
 * ausencia misteriosa de alertas.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProviderAttempt(
        String provider,
        boolean ok,
        boolean found,
        String error,
        int durationMs) {
}
