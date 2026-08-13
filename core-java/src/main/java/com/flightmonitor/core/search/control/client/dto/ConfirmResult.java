package com.flightmonitor.core.search.control.client.dto;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Resultado da confirmacao, com TRES desfechos distintos.
 *
 * <p>A distincao e o coracao do desenho da camada 2. Em especial,
 * {@link #naoExiste()} e {@link #degraded} levam a decisoes opostas na regra de
 * alerta (etapa E1.10):
 *
 * <ul>
 *   <li><b>confirmado</b> — ha voo real; use estes dados no alerta</li>
 *   <li><b>nao existe</b> — consultamos e nao ha voo assim. O candidato da
 *       camada 1 era ilusorio: <b>nao</b> alertar</li>
 *   <li><b>degradado</b> — nenhuma fonte respondeu; nao sabemos. Alertar mesmo
 *       assim, sem detalhe de voo, e melhor do que nao alertar</li>
 * </ul>
 *
 * Ver docs/FRAGILIDADE-CAMADA-2.md.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConfirmResult(
        boolean confirmed,
        boolean degraded,
        ConfirmedOffer offer,
        String provider,
        List<ProviderAttempt> attempts,
        List<String> warnings) {

    public ConfirmResult {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Consultamos com sucesso e o voo nao existe. Diferente de nao ter conseguido consultar. */
    public boolean naoExiste() {
        return !confirmed && !degraded;
    }

    /** Resposta usada quando o worker esta fora do ar: seguimos cegos, nao parados. */
    public static ConfirmResult degradado(String motivo) {
        return new ConfirmResult(false, true, null, null, List.of(), List.of(motivo));
    }
}
