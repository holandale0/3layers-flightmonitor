package com.flightmonitor.core.stats.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.stats.control.FlightScore.ComponenteDoScore;

/**
 * O Flight Score sob as preferências do monitor — etapa E2.6.
 *
 * <p>Até aqui a nota era a mesma para todo mundo. Quem viaja a trabalho quer voo
 * direto de manhã e paga por isso; quem viaja a passeio aceita escala e
 * madrugada para economizar. A mesma nota não serve para os dois.
 */
@SpringBootTest
class ScoreComPreferenciasTest {

    @Autowired
    private FlightScoreService service;

    @Autowired
    private ScoreProperties globais;

    private RouteStats referencia() {
        return new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS, 20, true,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("3600"), new BigDecimal("4000"), new BigDecimal("6000"),
                new BigDecimal("700"), Instant.now(), Instant.now());
    }

    private PriceObservation voo(String preco, short escalas, int duracao, int hora) {
        PriceObservation o = new PriceObservation(
                null, "GRU", "LIS", LocalDate.now().plusMonths(3),
                new BigDecimal(preco), PriceSource.FAST_FLIGHTS);
        o.setStops(escalas);
        o.setDurationMinutes(duracao);
        o.setDepartureAt(LocalDateTime.of(2027, 3, 10, hora, 30));
        o.setConfirmed(true);
        return o;
    }

    private Monitor monitor() {
        Monitor m = new Monitor();
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setMaxPrice(new BigDecimal("4000"));
        return m;
    }

    private ComponenteDoScore componente(FlightScore s, AspectoDoVoo aspecto) {
        return s.componentes().stream()
                .filter(c -> c.aspecto() == aspecto)
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------ preferência por direto

    @Test
    @DisplayName("preferir voo direto endurece a curva de escalas")
    void prefereDiretoEndureceAsEscalas() {
        PriceObservation comEscala = voo("3000", (short) 1, 700, 9);

        Monitor indiferente = monitor();
        Monitor exigente = monitor();
        exigente.setPrefereVooDireto(true);

        int notaNormal = componente(
                service.pontuar(comEscala, referencia(), 600, indiferente),
                AspectoDoVoo.ESCALAS).nota();
        int notaExigente = componente(
                service.pontuar(comEscala, referencia(), 600, exigente),
                AspectoDoVoo.ESCALAS).nota();

        assertThat(notaNormal).isEqualTo(65);
        assertThat(notaExigente).isEqualTo(30);
    }

    @Test
    @DisplayName("voo direto tira 100 nos dois casos")
    void diretoContinuaPerfeito() {
        PriceObservation direto = voo("3000", (short) 0, 600, 9);

        Monitor exigente = monitor();
        exigente.setPrefereVooDireto(true);

        assertThat(componente(service.pontuar(direto, referencia(), 600, exigente),
                AspectoDoVoo.ESCALAS).nota()).isEqualTo(100);
    }

    @Test
    @DisplayName("é preferência e não exigência: a oferta com escala continua pontuando")
    void continuaSendoPreferencia() {
        // Um voo com escala, muito barato, ainda pode valer a pena. Quem quer
        // exclusão de verdade usa maxStops, que nem chega a ser buscado.
        PriceObservation baratoComEscala = voo("2000", (short) 1, 600, 9);

        Monitor exigente = monitor();
        exigente.setPrefereVooDireto(true);

        FlightScore s = service.pontuar(baratoComEscala, referencia(), 600, exigente);

        assertThat(s.temNota()).isTrue();
        assertThat(s.nota()).isGreaterThan(50);
    }

    // ------------------------------------------------------------- pesos

    @Test
    @DisplayName("sem preferência de peso, a nota é a mesma de antes da E2.6")
    void semPreferenciaNadaMuda() {
        PriceObservation v = voo("3200", (short) 1, 700, 14);

        FlightScore semMonitor = service.pontuar(v, referencia(), 600);
        FlightScore comMonitorVazio = service.pontuar(v, referencia(), 600, monitor());

        assertThat(comMonitorVazio.nota()).isEqualTo(semMonitor.nota());
        assertThat(comMonitorVazio.cobertura()).isEqualByComparingTo(semMonitor.cobertura());
    }

    @Test
    @DisplayName("a sobrescrita é campo a campo, e não tudo ou nada")
    void sobrescritaCampoACampo() {
        Monitor m = monitor();
        // Só diz que escala incomoda muito. Não deveria ter que reinventar os
        // outros três pesos — e provavelmente escolheria pior que o padrão.
        m.setPesoEscalas((short) 60);

        FlightScore s = service.pontuar(voo("3200", (short) 1, 700, 14), referencia(), 600, m);

        assertThat(componente(s, AspectoDoVoo.ESCALAS).peso()).isEqualTo(60);
        assertThat(componente(s, AspectoDoVoo.PRECO).peso()).isEqualTo(globais.pesoPreco());
        assertThat(componente(s, AspectoDoVoo.DURACAO).peso()).isEqualTo(globais.pesoDuracao());
        assertThat(componente(s, AspectoDoVoo.HORARIO).peso()).isEqualTo(globais.pesoHorario());
    }

    @Test
    @DisplayName("peso maior em escalas derruba a nota de um voo com conexão")
    void pesoMudaANota() {
        PriceObservation comEscala = voo("3000", (short) 2, 700, 9);

        Monitor padrao = monitor();
        Monitor odeiaEscala = monitor();
        odeiaEscala.setPesoEscalas((short) 80);

        int notaPadrao = service.pontuar(comEscala, referencia(), 600, padrao).nota();
        int notaComPeso = service.pontuar(comEscala, referencia(), 600, odeiaEscala).nota();

        // O aspecto ruim passa a dominar a média ponderada.
        assertThat(notaComPeso).isLessThan(notaPadrao);
    }

    @Test
    @DisplayName("peso zero é escolha válida: o aspecto sai da conta")
    void pesoZeroEEscolhaValida() {
        // Voo de madrugada, que perderia pontos no horário.
        PriceObservation madrugada = voo("3000", (short) 0, 600, 3);

        Monitor naoLigaParaHorario = monitor();
        naoLigaParaHorario.setPesoHorario((short) 0);

        FlightScore s = service.pontuar(madrugada, referencia(), 600, naoLigaParaHorario);

        // Zero significa "não me importo com isso" — diferente de nulo, que é
        // "não escolhi". O horário continua sendo avaliado e mostrado, e apenas
        // deixa de pesar.
        assertThat(componente(s, AspectoDoVoo.HORARIO).peso()).isZero();
        assertThat(componente(s, AspectoDoVoo.HORARIO).nota()).isEqualTo(25);
        // Preco 3.000 esta EM p25, o que vale 80 — nao 100. Com escalas e
        // duracao em 100 e o horario fora da conta:
        // (80 x 50 + 100 x 20 + 100 x 20) / 90 = 89.
        assertThat(s.nota()).isEqualTo(89);
    }

    @Test
    @DisplayName("a cobertura é calculada sobre os pesos do monitor, não sobre os globais")
    void coberturaUsaOsPesosDoMonitor() {
        // Oferta de cache: sem duração e sem horário.
        PriceObservation cache = new PriceObservation(
                null, "GRU", "LIS", LocalDate.now().plusMonths(3),
                new BigDecimal("3000"), PriceSource.TRAVELPAYOUTS);
        cache.setStops((short) 0);

        Monitor m = monitor();
        m.setPesoPreco((short) 40);
        m.setPesoEscalas((short) 40);
        m.setPesoDuracao((short) 10);
        m.setPesoHorario((short) 10);

        FlightScore s = service.pontuar(cache, referencia(), null, m);

        // 40 + 40 de um total de 100. Usar o total global aqui daria outro
        // número, e a cobertura passaria a descrever uma escala que não é a
        // desta nota.
        assertThat(s.cobertura()).isEqualByComparingTo("0.80");
    }
}
