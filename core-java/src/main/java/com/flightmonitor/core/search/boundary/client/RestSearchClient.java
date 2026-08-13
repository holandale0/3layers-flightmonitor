package com.flightmonitor.core.search.boundary.client;

import com.flightmonitor.core.search.control.client.SearchClient;
import com.flightmonitor.core.search.control.client.WorkerUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;

/**
 * Adaptador REST da {@link SearchClient}.
 *
 * <p>As duas camadas tratam falha de formas deliberadamente opostas — ver o
 * contrato de erro na interface.
 */
@Component
@ConditionalOnProperty(
        name = "flightmonitor.worker.transporte",
        havingValue = "REST",
        // Ausente = REST. O transporte novo (E4.1) so entra quando pedido; o
        // padrao continua sendo o que ja rodava, e nao o recem-chegado.
        matchIfMissing = true)
public class RestSearchClient implements SearchClient {

    private static final Logger log = LoggerFactory.getLogger(RestSearchClient.class);

    private final RestClient scanClient;
    private final RestClient confirmClient;

    public RestSearchClient(
            @Qualifier("workerScanClient") RestClient scanClient,
            @Qualifier("workerConfirmClient") RestClient confirmClient) {
        this.scanClient = scanClient;
        this.confirmClient = confirmClient;
    }

    @Override
    public CalendarSearchResult scanCalendar(CalendarSearchCommand cmd) {
        try {
            CalendarSearchResult resultado = scanClient.post()
                    .uri("/search/calendar")
                    .body(cmd)
                    .retrieve()
                    .body(CalendarSearchResult.class);

            if (resultado == null) {
                throw new WorkerUnavailableException("o worker devolveu corpo vazio na varredura");
            }

            if (!resultado.warnings().isEmpty()) {
                log.info("varredura {}->{} com avisos: {}",
                        cmd.origin(), cmd.destination(), resultado.warnings());
            }
            return resultado;

        } catch (RestClientResponseException e) {
            throw new WorkerUnavailableException(
                    "o worker respondeu HTTP %d na varredura: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    e);
        } catch (RestClientException e) {
            // Timeout, conexao recusada, DNS. Sem preco nao ha varredura.
            throw new WorkerUnavailableException(
                    "nao foi possivel falar com o worker na varredura: " + e.getMessage(), e);
        }
    }

    @Override
    public ConfirmResult confirm(ConfirmCommand cmd) {
        try {
            ConfirmResult resultado = confirmClient.post()
                    .uri("/search/confirm")
                    .body(cmd)
                    .retrieve()
                    .body(ConfirmResult.class);

            if (resultado == null) {
                return degradar(cmd, "o worker devolveu corpo vazio na confirmacao");
            }

            if (resultado.degraded()) {
                log.warn("camada 2 degradada para {}->{} em {}: {}",
                        cmd.origin(), cmd.destination(), cmd.departureDate(), resultado.warnings());
            }
            return resultado;

        } catch (RestClientResponseException e) {
            return degradar(cmd, "o worker respondeu HTTP %d na confirmacao"
                    .formatted(e.getStatusCode().value()));
        } catch (RestClientException e) {
            return degradar(cmd, "nao foi possivel falar com o worker: " + e.getMessage());
        }
    }

    /**
     * Falha na camada 2 nunca vira excecao.
     *
     * <p>Derrubar a varredura por causa de uma camada opcional seria pior do que
     * seguir sem ela: um alerta sem detalhe de voo vale muito mais que alerta
     * nenhum. Ver D-028 e docs/FRAGILIDADE-CAMADA-2.md.
     */
    private ConfirmResult degradar(ConfirmCommand cmd, String motivo) {
        log.warn("confirmacao indisponivel para {}->{} em {}: {}",
                cmd.origin(), cmd.destination(), cmd.departureDate(), motivo);
        return ConfirmResult.degradado(motivo);
    }
}
