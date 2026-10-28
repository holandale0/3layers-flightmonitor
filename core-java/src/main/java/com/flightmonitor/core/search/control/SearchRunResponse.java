package com.flightmonitor.core.search.control;

import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.entity.SearchRun;
import com.flightmonitor.core.search.entity.SearchStatus;

import java.time.Instant;

/**
 * Uma varredura, como a tela de historico a mostra.
 *
 * <p>Existe para o historico vazio dizer a verdade: sem isto, a tela nao
 * distinguia "nunca buscou" de "buscou 20 vezes e a fonte nao tinha preco".
 */
public record SearchRunResponse(
        Long id,
        PriceSource source,
        SearchStatus status,
        Instant startedAt,
        Instant finishedAt,
        int observationsCount,
        String errorMessage) {

    public static SearchRunResponse de(SearchRun r) {
        return new SearchRunResponse(
                r.getId(),
                r.getSource(),
                r.getStatus(),
                r.getStartedAt(),
                r.getFinishedAt(),
                r.getObservationsCount(),
                r.getErrorMessage());
    }
}
