package com.flightmonitor.core.alert.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.PushDevice;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.control.SearchOutcome;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;

/**
 * Varios canais ativos ao mesmo tempo — D-113.
 *
 * <p>Os tres canais reais ligados juntos, e nenhum envio: o teste so olha os
 * alertas que a decisao cria. Quem entrega e o despacho, que nao roda aqui.
 */
@SpringBootTest
@TestPropertySource(properties = "flightmonitor.notification.canais=WHATSAPP,EMAIL,PUSH")
class AlertMulticanalTest {

    @Autowired
    private AlertService service;

    @Autowired
    private AlertRepository alertas;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

    @Autowired
    private PushDeviceRepository aparelhos;

    @Autowired
    private PriceObservationRepository observacoes;

    private Monitor monitor;
    private Recipient leonardo;
    private final LocalDate ida = LocalDate.now().plusMonths(6);

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        alertas.deleteAll();
        observacoes.deleteAll();
        monitores.deleteAll();
        aparelhos.deleteAll();
        destinatarios.deleteAll();
    }

    @BeforeEach
    void preparar() {
        limparBanco();

        leonardo = destinatarios.saveAndFlush(
                new Recipient("Leonardo", "+5511933332222", "leonardo@exemplo.com"));

        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(10));
        m.setMaxPrice(new BigDecimal("3200.00"));
        m.addRecipient(leonardo);
        monitor = monitores.saveAndFlush(m);
    }

    private AlertDecision alertarA(String preco) {
        PriceObservation o = new PriceObservation(
                monitor, "GRU", "LIS", ida, new BigDecimal(preco), PriceSource.FAST_FLIGHTS);
        o.setReturnDate(ida.plusDays(12));
        o.setCurrency("BRL");
        o.setConfirmed(true);
        o = observacoes.saveAndFlush(o);
        return service.avaliar(new SearchOutcome(
                monitor.getId(), 1, 1, o.getId(), o.getPrice(),
                true, false, false, false, List.of()));
    }

    private List<AlertChannel> canaisCriados() {
        return alertas.findAll().stream().map(Alert::getChannel).sorted().toList();
    }

    @Test
    @DisplayName("um alerta por canal que alcanca o destinatario")
    void umAlertaPorCanal() {
        aparelhos.saveAndFlush(new PushDevice(leonardo, "token-do-celular-do-leonardo-0001", "Pixel 8"));

        AlertDecision d = alertarA("2900");

        assertThat(d.alertar()).isTrue();
        assertThat(canaisCriados()).containsExactly(
                AlertChannel.WHATSAPP, AlertChannel.EMAIL, AlertChannel.PUSH);
        // A mesma mensagem nos tres: o canal muda o envelope, nao o conteudo.
        assertThat(alertas.findAll()).extracting(Alert::getMessage).containsOnly(
                alertas.findAll().get(0).getMessage());
    }

    @Test
    @DisplayName("sem aparelho, o PUSH nao cria alerta fadado a falhar")
    void semAparelhoNaoHaPush() {
        alertarA("2900");

        assertThat(canaisCriados()).containsExactly(AlertChannel.WHATSAPP, AlertChannel.EMAIL);
    }

    @Test
    @DisplayName("aparelho desligado nao conta como alcance")
    void aparelhoDesligadoNaoAlcanca() {
        PushDevice celular = new PushDevice(leonardo, "token-do-celular-do-leonardo-0002", "Pixel 8");
        celular.desativar("UNREGISTERED");
        aparelhos.saveAndFlush(celular);

        alertarA("2900");

        assertThat(canaisCriados()).doesNotContain(AlertChannel.PUSH);
    }

    @Test
    @DisplayName("tres linhas por oferta nao enganam o cooldown")
    void cooldownContinuaPorMonitor() {
        // O anti-spam olha o monitor, e nao o canal. Se olhasse o canal, cada
        // um teria seu proprio cooldown e a mesma oferta sairia tres vezes.
        alertarA("2900");
        AlertDecision segunda = alertarA("2500");

        assertThat(segunda.alertar()).isFalse();
        assertThat(segunda.motivo()).isEqualTo(AlertDecision.Motivo.DENTRO_DO_COOLDOWN);
        assertThat(alertas.count()).isEqualTo(2);
    }
}
