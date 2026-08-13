package com.flightmonitor.core.stats.boundary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;

/**
 * API de estatisticas — etapa E2.1.
 *
 * <p>Rota exclusiva ({@code CWB → EZE}) para nao enxergar dados de outros
 * testes: esta classe e transacional e faz rollback, mas as classes que nao sao
 * deixam linhas commitadas para tras.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private PriceObservationRepository observacoes;

    private Monitor monitor;
    private PriceObservation confirmada;

    private final LocalDate ida = LocalDate.of(2027, 7, 15);

    @BeforeEach
    void preparar() {
        Monitor m = new Monitor();
        m.setLabel("Buenos Aires");
        m.setOrigin("CWB");
        m.setDestination("EZE");
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(30));
        m.setMaxPrice(new BigDecimal("2500.00"));
        monitor = monitores.saveAndFlush(m);

        observar("1000", false, ida);
        observar("2000", false, ida);
        observar("3000", false, ida.plusMonths(1));
        confirmada = observar("4000", true, ida.plusMonths(1));
    }

    private PriceObservation observar(String preco, boolean confirmada, LocalDate partida) {
        PriceObservation o = new PriceObservation(
                monitor, "CWB", "EZE", partida, new BigDecimal(preco),
                confirmada ? PriceSource.FAST_FLIGHTS : PriceSource.TRAVELPAYOUTS);
        o.setConfirmed(confirmada);
        o.setCurrency("BRL");
        if (confirmada) {
            o.setStops((short) 0);
            o.setDurationMinutes(240);
            o.setDepartureAt(partida.atTime(9, 15));
        }
        return observacoes.saveAndFlush(o);
    }

    @Test
    @DisplayName("GET da rota devolve os numeros e a base de calculo")
    void estatisticasDaRota() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("CWB"))
                .andExpect(jsonPath("$.destination").value("EZE"))
                .andExpect(jsonPath("$.amostras").value(4))
                .andExpect(jsonPath("$.minimo").value(1000.00))
                .andExpect(jsonPath("$.mediana").value(2500.00))
                .andExpect(jsonPath("$.media").value(2500.00))
                .andExpect(jsonPath("$.maximo").value(4000.00))
                // A fonte vai na resposta, e nao so no pedido: quem le o JSON
                // depois precisa saber sobre o que estes numeros falam.
                .andExpect(jsonPath("$.fonte").value("TODAS"));
    }

    @Test
    @DisplayName("fonte=CONFIRMADAS muda a base, e a resposta diz isso")
    void filtraPorFonte() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE").param("fonte", "CONFIRMADAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("CONFIRMADAS"))
                .andExpect(jsonPath("$.amostras").value(1))
                .andExpect(jsonPath("$.media").value(4000.00))
                // Uma amostra nao sustenta referencia nenhuma.
                .andExpect(jsonPath("$.confiavel").value(false));
    }

    @Test
    @DisplayName("rota sem historico responde 200 com vazio, e nao 404")
    void rotaSemHistorico() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/SCL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amostras").value(0))
                .andExpect(jsonPath("$.confiavel").value(false));
    }

    @Test
    @DisplayName("o corte mensal sai em ordem, por mes de partida")
    void mesesDaRota() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].mes").value("2027-07"))
                .andExpect(jsonPath("$[0].amostras").value(2))
                .andExpect(jsonPath("$[1].mes").value("2027-08"))
                .andExpect(jsonPath("$[1].media").value(3500.00));
    }

    @Test
    @DisplayName("pelo monitor, o resultado e o mesmo da rota dele")
    void estatisticasDoMonitor() throws Exception {
        mvc.perform(get("/api/stats/monitors/" + monitor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("CWB"))
                .andExpect(jsonPath("$.amostras").value(4));
    }

    @Test
    @DisplayName("monitor inexistente devolve 404 com ProblemDetail")
    void monitorInexistente() throws Exception {
        mvc.perform(get("/api/stats/monitors/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("janela em dias restringe o periodo")
    void janelaEmDias() throws Exception {
        // Tudo foi observado agora, entao um dia ja cobre tudo — o que este
        // teste prova e que o parametro e aceito e aplicado sem quebrar.
        mvc.perform(get("/api/stats/routes/CWB/EZE").param("dias", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amostras").value(4));
    }

    @Test
    @DisplayName("a anomalia responde SEM_DADOS enquanto a rota tem pouca historia")
    void anomaliaSemHistoricoSuficiente() throws Exception {
        // Quatro observacoes, e o minimo configurado e oito. Um preco otimo
        // aqui nao pode virar superlativo.
        mvc.perform(get("/api/stats/routes/CWB/EZE/anomaly").param("preco", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grau").value("SEM_DADOS"))
                .andExpect(jsonPath("$.explicacao").exists())
                // A referencia vai junto: sem ela nao da para conferir o veredito.
                .andExpect(jsonPath("$.referencia.amostras").value(4));
    }

    @Test
    @DisplayName("a anomalia pelo monitor usa a rota dele")
    void anomaliaDoMonitor() throws Exception {
        mvc.perform(get("/api/stats/monitors/" + monitor.getId() + "/anomaly")
                        .param("preco", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco").value(500))
                .andExpect(jsonPath("$.referencia.origin").value("CWB"));
    }

    @Test
    @DisplayName("anomalia sem o parametro preco e erro do chamador")
    void anomaliaSemPreco() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE/anomaly"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o score de uma observacao traz os quatro aspectos e a cobertura")
    void scoreDaObservacao() throws Exception {
        mvc.perform(get("/api/stats/observations/" + confirmada.getId() + "/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").exists())
                .andExpect(jsonPath("$.componentes", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.cobertura").exists())
                // Poucas observacoes: a nota sai, mas nao como veredito.
                .andExpect(jsonPath("$.confiavel").value(false))
                .andExpect(jsonPath("$.explicacao").exists());
    }

    @Test
    @DisplayName("observacao inexistente devolve 404")
    void scoreDeObservacaoInexistente() throws Exception {
        mvc.perform(get("/api/stats/observations/999999/score"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a tendencia devolve a serie usada, e nao so a conclusao")
    void tendenciaDaRota() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direcao").exists())
                .andExpect(jsonPath("$.serie").isArray())
                // Tudo observado hoje: um dia so, e um dia nao e tendencia.
                .andExpect(jsonPath("$.diasComDados").value(1))
                .andExpect(jsonPath("$.direcao").value("SEM_DADOS"))
                .andExpect(jsonPath("$.confiavel").value(false));
    }

    @Test
    @DisplayName("a tendencia pelo monitor usa a rota dele")
    void tendenciaDoMonitor() throws Exception {
        mvc.perform(get("/api/stats/monitors/" + monitor.getId() + "/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("CWB"))
                .andExpect(jsonPath("$.destination").value("EZE"));
    }

    @Test
    @DisplayName("dias absurdo e limitado em vez de virar erro")
    void diasAbsurdoNaoQuebra() throws Exception {
        mvc.perform(get("/api/stats/routes/CWB/EZE").param("dias", "-50"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/stats/routes/CWB/EZE").param("dias", "99999999"))
                .andExpect(status().isOk());
    }
}
