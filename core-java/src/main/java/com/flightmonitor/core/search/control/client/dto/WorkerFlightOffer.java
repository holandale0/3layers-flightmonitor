package com.flightmonitor.core.search.control.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Uma oferta devolvida pela camada 1. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerFlightOffer(
        LocalDate departureDate,
        LocalDate returnDate,
        BigDecimal price,
        String currency,
        String airline,
        String flightNumber,
        Short stops,
        // Horario local do aeroporto — sem fuso, coerente com o schema do banco.
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        // Ate quando o preco cacheado vale. Este SIM tem fuso: e um instante.
        OffsetDateTime expiresAt,
        String source) {
}
