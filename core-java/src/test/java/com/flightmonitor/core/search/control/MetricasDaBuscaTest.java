package com.flightmonitor.core.search.control;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * As metricas da varredura — etapa E4.3.
 *
 * <p>O teste central aqui e {@link OSinalDoBug014}: ele reproduz a assinatura do
 * BUG-014 e verifica que ela <b>aparece</b> nas metricas. Metrica que nao pega o
 * bug que motivou sua existencia e decoracao.
 */
class MetricasDaBuscaTest {

    private MeterRegistry registro;
    private MetricasDaBusca metricas;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        metricas = new MetricasDaBusca(registro);
    }

    private double contador(String nome, String... tags) {
        return registro.find(nome).tags(tags).counter() == null
                ? 0
                : registro.find(nome).tags(tags).counter().count();
    }

    private static SearchOutcome varredura(
            int gravadas, int candidatos, boolean confirmada,
            boolean camada2Degradada, boolean ilusorio, boolean falhou) {

        return new SearchOutcome(
                1L, gravadas, candidatos,
                confirmada ? 10L : null,
                confirmada ? new BigDecimal("1000") : null,
                confirmada, camada2Degradada, ilusorio, falhou, List.of());
    }

    @Nested
    @DisplayName("desfecho da varredura")
    class Desfecho {

        @Test
        @DisplayName("fonte fora do ar conta como falha, e nao como 'sem candidato'")
        void falha() {
            // A distincao e o ponto: "nao achei nada" e "nao consegui procurar"
            // exigem reacoes diferentes de quem opera.
            metricas.registrar(varredura(0, 0, false, false, false, true), Duration.ofSeconds(1));

            assertThat(contador("flightmonitor.busca.execucoes", "desfecho", "falhou")).isOne();
            assertThat(contador("flightmonitor.busca.execucoes", "desfecho", "sem_candidato")).isZero();
        }

        @Test
        @DisplayName("dia sem promocao conta como sem candidato")
        void semCandidato() {
            metricas.registrar(varredura(30, 0, false, false, false, false), Duration.ofSeconds(1));

            assertThat(contador("flightmonitor.busca.execucoes", "desfecho", "sem_candidato")).isOne();
            assertThat(contador("flightmonitor.observacoes.gravadas")).isEqualTo(30);
        }

        @Test
        @DisplayName("candidato confirmado vira oportunidade")
        void comOportunidade() {
            metricas.registrar(varredura(30, 2, true, false, false, false), Duration.ofSeconds(1));

            assertThat(contador("flightmonitor.busca.execucoes", "desfecho", "com_oportunidade")).isOne();
        }

        @Test
        @DisplayName("o tempo de cada varredura e' medido")
        void mede() {
            metricas.registrar(varredura(1, 0, false, false, false, false), Duration.ofMillis(1500));

            assertThat(registro.find("flightmonitor.busca.duracao").timer().count()).isOne();
        }
    }

    @Nested
    @DisplayName("o sinal que teria pego o BUG-014")
    class OSinalDoBug014 {

        @Test
        @DisplayName("camada 2 ausente aparece como 'indisponivel', varredura apos varredura")
        void camada2Ausente() {
            // A assinatura exata do bug: a biblioteca da camada 2 nao estava
            // instalada, entao TODA varredura com candidato voltava degradada.
            // Nao houve erro, nao houve log vermelho — e ninguem percebeu por
            // seis semanas.
            for (int i = 0; i < 10; i++) {
                metricas.registrar(
                        varredura(30, 3, false, true, false, false), Duration.ofSeconds(2));
            }

            assertThat(contador("flightmonitor.camada2.consultas", "resultado", "indisponivel"))
                    .isEqualTo(10);
            assertThat(contador("flightmonitor.camada2.consultas", "resultado", "confirmou"))
                    .isZero();

            // E a busca continua "funcionando": e por isso que so olhar para
            // erro e uptime nao pegaria nada.
            assertThat(contador("flightmonitor.busca.execucoes", "desfecho", "falhou")).isZero();
        }

        @Test
        @DisplayName("sem candidato, a camada 2 nao e' contada")
        void semCandidatoNaoPoluiAMetrica() {
            // Dia sem promocao e a maioria dos dias. Contar isso como "nao
            // confirmou" afogaria o sinal de verdade no ruido.
            metricas.registrar(varredura(30, 0, false, false, false, false), Duration.ofSeconds(1));

            assertThat(registro.find("flightmonitor.camada2.consultas").counters()).isEmpty();
        }

        @Test
        @DisplayName("preco que sumiu na confirmacao aparece como ilusorio")
        void ilusorio() {
            // Diferente de indisponivel: aqui a camada 2 respondeu, e disse que
            // o preco nao existe. E fonte funcionando, nao fonte quebrada.
            metricas.registrar(varredura(30, 1, false, false, true, false), Duration.ofSeconds(1));

            assertThat(contador("flightmonitor.camada2.consultas", "resultado", "ilusorio")).isOne();
        }
    }

    @Test
    @DisplayName("a decisao de alerta e' contada por motivo")
    void decisoes() {
        metricas.registrarDecisao("ANTI_SPAM", false);
        metricas.registrarDecisao("ALERTADO", true);
        metricas.registrarDecisao("ANTI_SPAM", false);

        assertThat(contador("flightmonitor.alerta.decisoes", "motivo", "anti_spam", "alertou", "false"))
                .isEqualTo(2);
        assertThat(contador("flightmonitor.alerta.decisoes", "motivo", "alertado", "alertou", "true"))
                .isOne();
    }
}
