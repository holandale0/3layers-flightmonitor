package com.flightmonitor.core.agent.boundary;

import com.flightmonitor.core.agent.control.MonitorIntent;
import com.flightmonitor.core.agent.control.NlpPort;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.flightmonitor.core.search.control.client.WorkerUnavailableException;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Adaptador REST da {@link NlpPort} — etapa E3.1.
 *
 * <p>O nome mudou de {@code NlpClient} na reorganizacao BCE: o controle passou
 * a depender da PORTA, e nao desta classe. Trocar HTTP por outra coisa aqui nao
 * toca no {@code AgentService} — a mesma propriedade que permitiu a E4.1 trocar
 * REST por mensageria na busca.
 *
 * <h2>Por que a interpretacao vive no Python</h2>
 *
 * O ecossistema esta la, e a tarefa e de <b>especialista</b>: recebe texto,
 * devolve campos. Nao toca no banco, nao conhece monitores, nao decide se um
 * preco e bom — as regras 1 e 2 da secao 3 do PLANO-DE-ACAO continuam valendo.
 * Ver D-076.
 *
 * <p>O que fica no core e o que e do core: validar o que veio, decidir o que
 * fazer com o que faltou, e criar o monitor (E3.2). O worker relata; o core
 * decide.
 *
 * <h2>Contrato de erro</h2>
 *
 * <b>Lanca</b>, ao contrario da confirmacao de preco, que degrada. Nao ha
 * resposta parcial util aqui: sem interpretacao nao ha o que mostrar, e
 * devolver uma intencao vazia faria o usuario achar que o pedido dele nao
 * continha nada.
 */
@Component
public class RestNlpClient implements NlpPort {

    private static final Logger log = LoggerFactory.getLogger(RestNlpClient.class);

    private final RestClient client;

    public RestNlpClient(@Qualifier("workerConfirmClient") RestClient client) {
        // Reaproveita o cliente de confirmacao: os dois falam com o mesmo
        // worker e toleram resposta lenta — a confirmacao vai ao Google ao
        // vivo, e a interpretacao pode passar por um modelo.
        this.client = client;
    }

    @Override
    public MonitorIntent interpretar(String texto, String origemPadrao, LocalDate hoje) {
        try {
            MonitorIntent intent = client.post()
                    .uri("/nlp/intent")
                    .body(new Pedido(texto, hoje, origemPadrao))
                    .retrieve()
                    .body(MonitorIntent.class);

            if (intent == null) {
                throw new WorkerUnavailableException(
                        "o worker devolveu corpo vazio na interpretacao");
            }

            log.info("intencao: {}->{} por {} (confianca {})",
                    intent.origin(), intent.destination(), intent.provider(), intent.confianca());
            return intent;

        } catch (RestClientResponseException e) {
            throw new WorkerUnavailableException(
                    "o worker respondeu HTTP %d na interpretacao: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    e);
        } catch (RestClientException e) {
            throw new WorkerUnavailableException(
                    "nao foi possivel falar com o worker na interpretacao: " + e.getMessage(), e);
        }
    }

    /**
     * O pedido, em snake_case.
     *
     * @param hoje enviado de proposito, em vez de deixar o worker ler o proprio
     *        relogio. "Em marco" depende de quando se pergunta, e os dois
     *        processos podem estar em fusos ou containers diferentes — a
     *        resposta nao pode variar por causa disso
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record Pedido(String texto, LocalDate hoje, String origemPadrao) {
    }
}
