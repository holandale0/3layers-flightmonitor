package com.flightmonitor.core.search.control.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;

/**
 * Contrato contra o worker Python REAL.
 *
 * <p>Desligado por padrao. Roda com:
 * <pre>mvn test -Dtest=ContratoRealTest -Dworker.live=true</pre>
 * exigindo o worker de pe na porta 8001.
 *
 * <p><b>Por que existe:</b> o {@link SearchClientTest} usa WireMock com JSON que
 * <em>eu</em> escrevi. Se eu tiver entendido o formato errado, ele passa mesmo
 * assim — validaria a minha suposicao, nao o contrato. Este teste fala com o
 * worker de verdade e e o unico que detecta divergencia entre as duas
 * linguagens.
 *
 * <p>Fora do build padrao de proposito: depende de outro processo no ar e de
 * rede externa. E a semente da etapa E1.16, que vai formalizar isso com
 * providers falsos, tornando-o deterministico.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "worker.live", matches = "true")
class ContratoRealTest {

    @Autowired
    private SearchClient client;

    @Test
    @DisplayName("varredura real: o Java desserializa o que o Python realmente devolve")
    void varreduraReal() {
        LocalDate de = LocalDate.now().plusDays(30);

        CalendarSearchResult r = client.scanCalendar(new CalendarSearchCommand(
                "GRU", "LIS", de, de.plusDays(25), null, null, "BRL", (short) 2));

        // Nao afirmamos que ha ofertas: isso depende do cache da fonte externa.
        // Afirmamos que o CONTRATO foi respeitado.
        assertThat(r).isNotNull();
        assertThat(r.origin()).isEqualTo("GRU");
        assertThat(r.destination()).isEqualTo("LIS");
        assertThat(r.returned()).isGreaterThanOrEqualTo(r.kept());
        assertThat(r.offers()).hasSize(r.kept());

        System.out.printf("[contrato real] varredura: %d recebidas, %d mantidas, provider_origin=%s%n",
                r.returned(), r.kept(), r.providerOrigin());

        r.offers().stream().findFirst().ifPresent(o -> {
            assertThat(o.departureDate()).isNotNull();
            assertThat(o.price()).isPositive();
            assertThat(o.currency()).isEqualTo("BRL");
            assertThat(o.source()).isEqualTo("TRAVELPAYOUTS");
            // A oferta tem que respeitar a janela pedida (RISCO-007).
            assertThat(o.departureDate()).isBetween(de, de.plusDays(25));
            System.out.printf("[contrato real] melhor oferta: %s por R$ %s (%s)%n",
                    o.departureDate(), o.price(), o.airline());
        });
    }

    @Test
    @DisplayName("confirmacao real: os tres desfechos sao representaveis")
    void confirmacaoReal() {
        LocalDate ida = LocalDate.now().plusDays(45);

        ConfirmResult r = client.confirm(new ConfirmCommand(
                "GRU", "LIS", ida, ida.plusDays(12),
                "BRL", (short) 2, (short) 1, new BigDecimal("3000")));

        assertThat(r).isNotNull();
        // Exatamente um dos tres desfechos, nunca dois.
        assertThat(r.confirmed() || r.degraded() || r.naoExiste()).isTrue();
        assertThat(r.confirmed() && r.degraded()).isFalse();

        System.out.printf("[contrato real] confirmacao: confirmado=%s degradado=%s via=%s avisos=%s%n",
                r.confirmed(), r.degraded(), r.provider(), r.warnings());

        if (r.confirmed()) {
            assertThat(r.offer()).isNotNull();
            assertThat(r.offer().price()).isPositive();
            assertThat(r.offer().source()).isEqualTo("FAST_FLIGHTS");
            assertThat(r.attempts()).isNotEmpty();
            System.out.printf("[contrato real] voo: %s %s->%s R$ %s, %s escalas%n",
                    r.offer().airline(), r.offer().departureAirport(),
                    r.offer().arrivalAirport(), r.offer().price(), r.offer().stops());
        }
    }
}
