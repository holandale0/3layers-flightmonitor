package com.flightmonitor.core.search.control;

import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.entity.PriceObservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ObservationResponse(
        Long id,
        String origin,
        String destination,
        LocalDate departureDate,
        LocalDate returnDate,
        BigDecimal price,
        String currency,
        String airline,
        Short stops,
        Integer durationMinutes,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        PriceSource source,
        boolean confirmed,
        Instant observedAt) {

    public static ObservationResponse de(PriceObservation o) {
        return new ObservationResponse(
                o.getId(),
                o.getOrigin(),
                o.getDestination(),
                o.getDepartureDate(),
                o.getReturnDate(),
                o.getPrice(),
                o.getCurrency(),
                o.getAirline(),
                o.getStops(),
                o.getDurationMinutes(),
                o.getDepartureAt(),
                o.getArrivalAt(),
                o.getSource(),
                o.isConfirmed(),
                o.getObservedAt());
    }
}
