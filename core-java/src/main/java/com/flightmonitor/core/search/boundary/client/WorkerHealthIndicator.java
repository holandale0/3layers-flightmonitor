package com.flightmonitor.core.search.boundary.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Expoe a saude do worker em {@code /actuator/health}.
 *
 * <p>Sem isto, um worker fora do ar so apareceria como ausencia de alertas —
 * o pior sintoma possivel, porque e indistinguivel de "nao houve oportunidade".
 *
 * <p>O status agregado NAO cai quando a camada 2 esta degradada: o sistema
 * continua funcional sem ela. Degradacao aparece nos detalhes, nao no status.
 */
@Component("worker")
public class WorkerHealthIndicator implements HealthIndicator {

    private final RestClient client;

    public WorkerHealthIndicator(@Qualifier("workerScanClient") RestClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> corpo = client.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);

            if (corpo == null) {
                return Health.down().withDetail("motivo", "resposta vazia").build();
            }

            Object providers = corpo.get("providers");
            return Health.up()
                    .withDetail("service", corpo.get("service"))
                    .withDetail("version", corpo.get("version"))
                    .withDetail("providers", providers)
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("motivo", e.getMessage())
                    .withDetail("impacto", "sem varredura de precos ate o worker voltar")
                    .build();
        }
    }
}
