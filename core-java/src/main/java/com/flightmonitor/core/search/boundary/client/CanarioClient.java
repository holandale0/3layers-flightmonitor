package com.flightmonitor.core.search.boundary.client;

import com.flightmonitor.core.search.control.canario.CanarioPort;
import com.flightmonitor.core.search.control.canario.ResultadoDoCanario;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fala com o canario do worker — etapa E4.5.
 *
 * <p>Adaptador magro de proposito: o worker e quem conhece as fontes, entao e
 * ele quem decide o que e formato valido. Aqui so se traduz protocolo.
 *
 * <p><b>Sempre por REST, mesmo com AMQP ligado.</b> O canario e diagnostico:
 * quando ele importa, alguma coisa ja esta errada, e uma resposta que depende da
 * fila viraria "sem resposta" quando o problema for a propria fila. Chamada
 * direta responde ou falha na hora, e as duas coisas sao informacao.
 */
@Component
public class CanarioClient implements CanarioPort {

    private final RestClient client;

    public CanarioClient(@Qualifier("workerScanClient") RestClient client) {
        this.client = client;
    }

    @Override
    public ResultadoDoCanario consultar() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> corpo = client.get()
                    .uri("/canario")
                    .retrieve()
                    .body(Map.class);

            if (corpo == null) {
                return ResultadoDoCanario.indisponivel("o worker respondeu vazio");
            }

            boolean saudavel = Boolean.TRUE.equals(corpo.get("saudavel"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> camadas =
                    (List<Map<String, Object>>) corpo.getOrDefault("camadas", List.of());

            return new ResultadoDoCanario(saudavel, camadas.stream()
                    .map(CanarioClient::traduzir)
                    .toList(), null);

        } catch (Exception e) {
            // Worker fora do ar tambem e resposta: significa que nao da para
            // saber como estao as fontes, que e diferente de "as fontes estao
            // bem". Ver o principio de dizer o que nao se sabe.
            return ResultadoDoCanario.indisponivel(
                    "nao foi possivel consultar o canario: " + e.getMessage());
        }
    }

    private static ResultadoDoCanario.Camada traduzir(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        List<String> achados = (List<String>) c.getOrDefault("achados", List.of());

        return new ResultadoDoCanario.Camada(
                String.valueOf(c.get("camada")),
                String.valueOf(c.get("provider")),
                Boolean.TRUE.equals(c.get("formato_ok")),
                c.get("erro") == null ? null : String.valueOf(c.get("erro")),
                achados);
    }
}
