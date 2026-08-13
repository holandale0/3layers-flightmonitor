package com.flightmonitor.core.search.control.client.dto;

import java.time.LocalDate;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Pedido de varredura enviado ao worker (camada 1).
 *
 * <p>O worker fala snake_case (convencao Python) e a nossa API fala camelCase.
 * A traducao fica confinada aos DTOs de cliente, via {@code @JsonNaming} por
 * classe — mudar a estrategia global do Jackson quebraria a nossa propria API.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CalendarSearchCommand(
        String origin,
        String destination,
        LocalDate departureFrom,
        LocalDate departureTo,
        LocalDate returnFrom,
        LocalDate returnTo,
        String currency,
        Short maxStops) {
}
