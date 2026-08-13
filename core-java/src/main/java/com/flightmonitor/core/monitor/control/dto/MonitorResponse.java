package com.flightmonitor.core.monitor.control.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;

import com.flightmonitor.core.monitor.entity.Monitor;

public record MonitorResponse(
        Long id,
        String label,
        String origin,
        String destination,
        LocalDate departureWindowStart,
        LocalDate departureWindowEnd,
        LocalDate returnWindowStart,
        LocalDate returnWindowEnd,
        Short minStayDays,
        Short maxStayDays,
        BigDecimal maxPrice,
        String currency,
        Short maxStops,
        short passengers,
        boolean active,
        int searchIntervalMinutes,
        Instant lastSearchedAt,
        Instant nextSearchAt,
        Instant createdAt,
        Instant updatedAt,
        List<RecipientSummary> recipients,

        // ------------------------------------------------ preferencias (E2.6)
        boolean prefereVooDireto,
        /** Em ordem alfabetica, para a resposta nao mudar entre chamadas. */
        List<String> avoidedAirlines,
        Short pesoPreco,
        Short pesoEscalas,
        Short pesoDuracao,
        Short pesoHorario) {

    public static MonitorResponse de(Monitor m) {
        return new MonitorResponse(
                m.getId(),
                m.getLabel(),
                m.getOrigin(),
                m.getDestination(),
                m.getDepartureWindowStart(),
                m.getDepartureWindowEnd(),
                m.getReturnWindowStart(),
                m.getReturnWindowEnd(),
                m.getMinStayDays(),
                m.getMaxStayDays(),
                m.getMaxPrice(),
                m.getCurrency(),
                m.getMaxStops(),
                m.getPassengers(),
                m.isActive(),
                m.getSearchIntervalMinutes(),
                m.getLastSearchedAt(),
                m.getNextSearchAt(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getRecipients().stream()
                        .map(RecipientSummary::de)
                        .sorted(Comparator.comparing(RecipientSummary::id))
                        .toList(),
                m.isPrefereVooDireto(),
                m.getAvoidedAirlines().stream().sorted().toList(),
                m.getPesoPreco(),
                m.getPesoEscalas(),
                m.getPesoDuracao(),
                m.getPesoHorario());
    }
}
