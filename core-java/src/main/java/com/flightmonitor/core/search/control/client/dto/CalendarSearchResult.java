package com.flightmonitor.core.search.control.client.dto;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Resultado da varredura.
 *
 * <p>{@code returned} e {@code kept} nao sao decoracao: permitem distinguir
 * "a janela nao tem oferta" de "a fonte esta fora do ar" sem ler log. Ver a
 * secao 4 de docs/PLANO-DE-ACAO.md.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CalendarSearchResult(
        String origin,
        String destination,
        List<WorkerFlightOffer> offers,
        int returned,
        int kept,
        String providerOrigin,
        String providerDestination,
        List<String> warnings) {

    public CalendarSearchResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** A fonte respondeu, mas nada caiu dentro dos criterios pedidos. */
    public boolean vazioAposFiltro() {
        return returned > 0 && kept == 0;
    }
}
