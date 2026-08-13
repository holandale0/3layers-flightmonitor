package com.flightmonitor.core.search.control.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Pedido de confirmacao de um candidato (camada 2). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConfirmCommand(
        String origin,
        String destination,
        LocalDate departureDate,
        LocalDate returnDate,
        String currency,
        Short maxStops,
        short passengers,
        /** Preco visto pela camada 1, para o worker medir a divergencia. */
        BigDecimal candidatePrice) {
}
