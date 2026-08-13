package com.flightmonitor.core.search.control.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Oferta confirmada pela camada 2, com os dados de voo que a camada 1 nao tem. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConfirmedOffer(
        LocalDate departureDate,
        LocalDate returnDate,
        BigDecimal price,
        String currency,
        String airline,
        String airlineCode,
        Short stops,
        Integer durationMinutes,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        /** Aeroporto real: a camada 1 so devolve codigo de cidade (RISCO-006). */
        String departureAirport,
        String arrivalAirport,
        String source) {
}
