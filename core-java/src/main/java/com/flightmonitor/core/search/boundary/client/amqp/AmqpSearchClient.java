package com.flightmonitor.core.search.boundary.client.amqp;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import com.flightmonitor.core.search.control.client.SearchClient;
import com.flightmonitor.core.search.boundary.client.WorkerProperties;
import com.flightmonitor.core.search.control.client.WorkerUnavailableException;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;

/**
 * O mesmo contrato de busca, por mensageria — etapa E4.1.
 *
 * <h2>Sincrono na semantica, assincrono no transporte</h2>
 *
 * Usa <b>request/reply</b> do AMQP: publica o pedido, espera a resposta na fila
 * temporaria, devolve. Do ponto de vista de quem chama, e identico ao adaptador
 * REST — e isso e a coisa mais importante deste desenho.
 *
 * <p>A alternativa seria tornar a busca de fato assincrona: publicar e seguir,
 * processando a resposta depois. Ela quebraria coisas que custaram caro:
 *
 * <ul>
 *   <li>o {@code processarMonitor} deixaria de ser um caminho unico, e o
 *       endpoint manual precisaria de um segundo fluxo — que e exatamente a
 *       forma do BUG-005;</li>
 *   <li>o estado entre pedido e resposta precisaria viver em algum lugar, e a
 *       varredura viraria uma maquina de estados distribuida para resolver um
 *       problema que este sistema ainda nao tem.</li>
 * </ul>
 *
 * <p>O que se ganha aqui e real mesmo sem assincronia: o broker no meio
 * desacopla os dois processos, a fila absorve rajada, o worker pode ser
 * replicado sem o core saber, e mensagem que ninguem processa fica visivel na
 * dead-letter em vez de sumir.
 *
 * <h2>O contrato de erro nao muda</h2>
 *
 * Varredura lanca, confirmacao degrada — igual ao REST. E a interface que manda,
 * nao o transporte.
 */
public class AmqpSearchClient implements SearchClient {

    private static final Logger log = LoggerFactory.getLogger(AmqpSearchClient.class);

    /**
     * O tipo esperado de cada resposta.
     *
     * <p>Constantes, e nao criados por chamada: cada
     * {@code new ParameterizedTypeReference<>() {}} e uma classe anonima nova, e
     * a varredura acontece a cada ciclo de cada monitor.
     */
    private static final ParameterizedTypeReference<CalendarSearchResult> TIPO_DA_VARREDURA =
            new ParameterizedTypeReference<>() { };

    private static final ParameterizedTypeReference<ConfirmResult> TIPO_DA_CONFIRMACAO =
            new ParameterizedTypeReference<>() { };

    private final RabbitTemplate template;
    private final WorkerProperties props;

    public AmqpSearchClient(RabbitTemplate template, WorkerProperties props) {
        this.template = template;
        this.props = props;
    }

    @Override
    public CalendarSearchResult scanCalendar(CalendarSearchCommand cmd) {
        CalendarSearchResult resultado = pedir(
                FilasDaBusca.ROTA_CALENDARIO, cmd, TIPO_DA_VARREDURA,
                props.scanTimeout(), "varredura");

        if (resultado == null) {
            // Nulo aqui e timeout: o RabbitTemplate devolve null quando a
            // resposta nao chega. Sem preco nao ha varredura.
            throw new WorkerUnavailableException(
                    "o worker nao respondeu a varredura em " + props.scanTimeout());
        }

        if (!resultado.warnings().isEmpty()) {
            log.info("varredura {}->{} com avisos: {}",
                    cmd.origin(), cmd.destination(), resultado.warnings());
        }
        return resultado;
    }

    @Override
    public ConfirmResult confirm(ConfirmCommand cmd) {
        ConfirmResult resultado;
        try {
            resultado = pedir(
                    FilasDaBusca.ROTA_CONFIRMACAO, cmd, TIPO_DA_CONFIRMACAO,
                    props.confirmTimeout(), "confirmacao");
        } catch (WorkerUnavailableException e) {
            // Degrada, nao lanca: derrubar a varredura por causa de uma camada
            // opcional seria pior do que seguir sem ela.
            return ConfirmResult.degradado(e.getMessage());
        }

        if (resultado == null) {
            return ConfirmResult.degradado(
                    "o worker nao respondeu a confirmacao em " + props.confirmTimeout());
        }
        return resultado;
    }

    /**
     * Publica e espera a resposta.
     *
     * <p>O timeout vai <b>tambem</b> no cabecalho {@code expiration} da
     * mensagem: sem isso, um pedido que ja expirou do lado de ca continuaria na
     * fila, e o worker gastaria uma consulta ao Google para produzir uma
     * resposta que ninguem esta mais esperando.
     */
    private <T> T pedir(
            String rota, Object corpo, ParameterizedTypeReference<T> tipo,
            Duration timeout, String operacao) {

        MessagePostProcessor validade = mensagem -> {
            mensagem.getMessageProperties().setExpiration(String.valueOf(timeout.toMillis()));
            return mensagem;
        };

        try {
            template.setReplyTimeout(timeout.toMillis());

            SmartMessageConverter conversor =
                    (SmartMessageConverter) template.getMessageConverter();

            Message pedido = validade.postProcessMessage(
                    conversor.toMessage(corpo, new MessageProperties()));

            Message resposta = template.sendAndReceive(FilasDaBusca.EXCHANGE, rota, pedido);
            if (resposta == null) {
                return null;
            }

            // O cabecalho e lido ANTES da conversao: a resposta de falha nao tem
            // corpo util, e converte-la produziria um objeto vazio que o motor
            // leria como "nao havia oferta".
            Object falha = resposta.getMessageProperties()
                    .getHeader(FilasDaBusca.CABECALHO_FALHA);
            if (falha != null) {
                throw new WorkerUnavailableException(
                        "a fonte falhou na %s: %s".formatted(operacao, falha));
            }

            // QUEM RECEBE declara o tipo. A alternativa seria o worker anunciar
            // a classe num cabecalho, o que faria o Python depender do desenho
            // interno do Java — e foi o que quebrou na primeira tentativa, com
            // "not in the trusted packages".
            @SuppressWarnings("unchecked")
            T convertida = (T) conversor.fromMessage(resposta, tipo);
            return convertida;

        } catch (AmqpException e) {
            // Broker fora do ar, credencial errada, exchange inexistente. Do
            // ponto de vista de quem chama e a mesma coisa que o worker estar
            // fora: nao ha resposta.
            throw new WorkerUnavailableException(
                    "falha ao falar com o worker por mensageria na %s: %s"
                            .formatted(operacao, e.getMessage()), e);
        }
    }
}
