package com.flightmonitor.core.search.boundary.client.amqp;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flightmonitor.core.search.control.client.SearchClient;
import com.flightmonitor.core.search.boundary.client.WorkerProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * A topologia da mensageria, e a troca do transporte — etapa E4.1.
 *
 * <h2>Quem declara as filas</h2>
 *
 * O <b>core</b>, e nao o worker. Os dois lados poderiam declarar — o AMQP torna
 * isso idempotente —, mas com dois donos uma divergencia de argumento (durable,
 * dead-letter, TTL) vira erro de canal na hora da conexao, e o servico que subir
 * primeiro ganha. Um dono so elimina a corrida.
 *
 * <p>O worker apenas consome de filas que espera existir. Se ele subir antes, o
 * consumo falha e ele tenta de novo — que e o comportamento certo.
 */
@Configuration
@ConditionalOnProperty(name = "flightmonitor.worker.transporte", havingValue = "AMQP")
public class AmqpConfig {

    @Bean
    DirectExchange exchangeDaBusca() {
        return new DirectExchange(FilasDaBusca.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange exchangeMorta() {
        return new DirectExchange(FilasDaBusca.EXCHANGE_MORTA, true, false);
    }

    @Bean
    Queue filaMorta() {
        return QueueBuilder.durable(FilasDaBusca.FILA_MORTA).build();
    }

    @Bean
    Binding bindingMorta() {
        return BindingBuilder.bind(filaMorta()).to(exchangeMorta()).with("#");
    }

    @Bean
    Queue filaCalendario() {
        return comDeadLetter(FilasDaBusca.FILA_CALENDARIO);
    }

    @Bean
    Queue filaConfirmacao() {
        return comDeadLetter(FilasDaBusca.FILA_CONFIRMACAO);
    }

    @Bean
    Binding bindingCalendario() {
        return BindingBuilder.bind(filaCalendario())
                .to(exchangeDaBusca()).with(FilasDaBusca.ROTA_CALENDARIO);
    }

    @Bean
    Binding bindingConfirmacao() {
        return BindingBuilder.bind(filaConfirmacao())
                .to(exchangeDaBusca()).with(FilasDaBusca.ROTA_CONFIRMACAO);
    }

    /**
     * Fila duravel, com saida para a dead-letter.
     *
     * <p>Sem o desvio, uma mensagem rejeitada volta para a fila e e reentregue
     * para sempre — o laco de veneno, que aparece como lentidao e nao como erro.
     */
    private Queue comDeadLetter(String nome) {
        return QueueBuilder.durable(nome)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", FilasDaBusca.EXCHANGE_MORTA,
                        "x-dead-letter-routing-key", nome))
                .build();
    }

    /**
     * JSON no corpo, com o MESMO ObjectMapper do resto da aplicacao.
     *
     * <p>Reaproveitar o mapper nao e economia: e o que garante que
     * {@code departure_date} continue sendo {@code departure_date} na
     * mensageria. Um mapper proprio aqui usaria camelCase, e o worker — que nao
     * mudou — deixaria de entender o pedido. O contrato entre os dois servicos e
     * o mesmo, so o meio de transporte muda.
     */
    @Bean
    MessageConverter conversorDaBusca(JsonMapper mapper) {
        return new org.springframework.amqp.support.converter.JacksonJsonMessageConverter(mapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory conexao, MessageConverter conversor) {
        RabbitTemplate template = new RabbitTemplate(conexao);
        template.setMessageConverter(conversor);
        // Mensagem que nao encontra fila volta em vez de sumir em silencio — e
        // o unico jeito de descobrir nome de rota errado sem esperar timeout.
        template.setMandatory(true);
        return template;
    }

    /**
     * O adaptador AMQP no lugar do REST.
     *
     * <p>Troca de transporte por configuracao, sem que nada que consome
     * {@link SearchClient} saiba — e exatamente o que o javadoc daquela
     * interface previa desde a E1.7.
     */
    @Bean
    SearchClient amqpSearchClient(RabbitTemplate template, WorkerProperties props) {
        return new AmqpSearchClient(template, props);
    }
}
