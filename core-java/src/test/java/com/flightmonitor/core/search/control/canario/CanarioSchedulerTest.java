package com.flightmonitor.core.search.control.canario;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O canario agendado — etapa E4.5.
 *
 * <p>O que se verifica aqui nao e a sonda (isso e do worker, e tem teste la):
 * e a <b>traducao para sinal</b>. Um canario que detecta o problema e nao o
 * torna visivel nao serve para nada.
 */
class CanarioSchedulerTest {

    private static final ResultadoDoCanario.Camada OK =
            new ResultadoDoCanario.Camada("1", "travelpayouts", true, null, List.of());

    private static final ResultadoDoCanario.Camada QUEBRADA =
            new ResultadoDoCanario.Camada("2", "fastflights", false, null,
                    List.of("offers[0].airline: esperava str ou None, veio list"));

    private CanarioScheduler comResultado(ResultadoDoCanario resultado) {
        MeterRegistry registro = new SimpleMeterRegistry();
        CanarioScheduler agendador = new CanarioScheduler(() -> resultado, registro);
        agendador.verificar();
        return agendador;
    }

    private double medida(CanarioScheduler agendador, MeterRegistry registro) {
        return registro.find("flightmonitor.canario.saudavel").gauge().value();
    }

    @Test
    @DisplayName("fontes saudaveis marcam a metrica em 1")
    void saudavel() {
        MeterRegistry registro = new SimpleMeterRegistry();
        new CanarioScheduler(
                () -> new ResultadoDoCanario(true, List.of(OK), null), registro).verificar();

        assertThat(registro.find("flightmonitor.canario.saudavel").gauge().value()).isOne();
    }

    @Test
    @DisplayName("formato mudou marca a metrica em 0 e guarda o que quebrou")
    void formatoMudou() {
        MeterRegistry registro = new SimpleMeterRegistry();
        CanarioScheduler agendador = new CanarioScheduler(
                () -> new ResultadoDoCanario(false, List.of(OK, QUEBRADA), null), registro);
        agendador.verificar();

        assertThat(registro.find("flightmonitor.canario.saudavel").gauge().value()).isZero();
        assertThat(agendador.ultimoResultado().comProblema())
                .singleElement()
                .satisfies(c -> assertThat(c.provider()).isEqualTo("fastflights"));
    }

    @Test
    @DisplayName("worker fora do ar e' -1, e nao 0")
    void naoConsultado() {
        // A distincao que mais importa: 0 manda olhar as fontes, -1 manda olhar
        // o worker. Confundir os dois faz procurar no lugar errado justamente
        // quando o tempo importa.
        MeterRegistry registro = new SimpleMeterRegistry();
        CanarioScheduler agendador = new CanarioScheduler(
                () -> ResultadoDoCanario.indisponivel("connection refused"), registro);
        agendador.verificar();

        assertThat(registro.find("flightmonitor.canario.saudavel").gauge().value()).isEqualTo(-1);
        assertThat(agendador.ultimoResultado().consultou()).isFalse();
    }

    @Test
    @DisplayName("antes da primeira execucao, nao ha resultado — e a metrica diz -1")
    void antesDeRodar() {
        MeterRegistry registro = new SimpleMeterRegistry();
        CanarioScheduler agendador = new CanarioScheduler(
                () -> new ResultadoDoCanario(true, List.of(OK), null), registro);

        // Sem chamar verificar(): o gauge nao pode fingir saude que ninguem mediu.
        assertThat(agendador.ultimoResultado()).isNull();
        assertThat(registro.find("flightmonitor.canario.saudavel").gauge().value()).isEqualTo(-1);
    }

    @Test
    @DisplayName("registra quando foi a ultima verificacao")
    void guardaOInstante() {
        CanarioScheduler agendador = comResultado(
                new ResultadoDoCanario(true, List.of(OK), null));

        // Sem isso, "saudavel" poderia ser de uma semana atras e ninguem saberia.
        assertThat(agendador.verificadoEm()).isNotNull();
    }
}
