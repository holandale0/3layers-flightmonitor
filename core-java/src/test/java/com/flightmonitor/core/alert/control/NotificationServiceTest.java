package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertStatus;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.entity.Alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;

/**
 * Testa a entrega dos alertas.
 *
 * <p>O foco esta nas FALHAS: o canal de log nunca falha, entao um teste do
 * caminho feliz sozinho nao provaria nada sobre retentativa. O canal falso
 * permite simular exatamente o que a Meta vai fazer na etapa E1.12.
 */
@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private AlertRepository alertas;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private NotificationProperties props;

    @Autowired
    private CanalFalso canal;

    private Monitor monitor;
    private Recipient destinatario;

    @TestConfiguration
    static class Config {
        @Bean
        CanalFalso canalFalso() {
            return new CanalFalso();
        }
    }

    /** Canal controlavel: entrega, falha transitoria ou falha permanente. */
    static class CanalFalso implements NotificationChannel {
        DeliveryResult resposta = DeliveryResult.entregue("falso:1");
        RuntimeException explode;
        final List<Long> enviados = new ArrayList<>();

        void limpar() {
            resposta = DeliveryResult.entregue("falso:1");
            explode = null;
            enviados.clear();
        }

        @Override
        public AlertChannel canal() {
            // Ocupa o slot do WHATSAPP para nao competir com o canal de log real.
            return AlertChannel.WHATSAPP;
        }

        @Override
        public DeliveryResult enviar(Alert alerta) {
            enviados.add(alerta.getId());
            if (explode != null) {
                throw explode;
            }
            if (!resposta.sucesso()) {
                return resposta;
            }
            // Um identificador POR ALERTA, e nao um fixo para todos.
            //
            // A primeira versao devolvia sempre "falso:1", e o indice unico da
            // migracao V4 recusou o segundo alerta do lote. O indice esta certo:
            // dois alertas com o mesmo provider_message_id tornariam o webhook
            // ambiguo — a confirmacao de entrega de um marcaria o outro. O dublê
            // e que estava produzindo algo que nenhum provedor real produz.
            return DeliveryResult.entregue(resposta.providerMessageId() + ":" + alerta.getId());
        }
    }

    @AfterEach
    void limparDepois() {
        limparBanco();
    }

    private void limparBanco() {
        alertas.deleteAll();
        observacoes.deleteAll();
        monitores.deleteAll();
        destinatarios.deleteAll();
    }

    @BeforeEach
    void preparar() {
        limparBanco();
        canal.limpar();

        destinatario = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511911112222"));

        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(LocalDate.now().plusMonths(6));
        m.setDepartureWindowEnd(LocalDate.now().plusMonths(6).plusDays(10));
        m.setMaxPrice(new BigDecimal("3200.00"));
        monitor = monitores.saveAndFlush(m);
    }

    private Alert alertaPendente(AlertChannel canalDoAlerta) {
        PriceObservation o = observacoes.saveAndFlush(new PriceObservation(
                monitor, "GRU", "LIS", LocalDate.now().plusMonths(6),
                new BigDecimal("2980.00"), PriceSource.FAST_FLIGHTS));

        Alert a = new Alert(monitor, o, destinatario, "✈️ Oportunidade: R$ 2.980");
        a.setChannel(canalDoAlerta);
        return alertas.saveAndFlush(a);
    }

    // ------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("alerta pendente e entregue e marcado como SENT")
    void entregaComSucesso() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);

        DispatchResult r = service.despacharPendentes();

        assertThat(r.reivindicados()).isEqualTo(1);
        assertThat(r.entregues()).isEqualTo(1);

        Alert entregue = alertas.findById(a.getId()).orElseThrow();
        assertThat(entregue.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(entregue.getSentAt()).isNotNull();
        assertThat(entregue.getProviderMessageId()).startsWith("falso:1:");
        assertThat(entregue.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("o canal de log entrega de verdade, sem credencial nenhuma")
    void canalDeLogEntrega() {
        Alert a = alertaPendente(AlertChannel.LOG);

        service.despacharPendentes();

        Alert entregue = alertas.findById(a.getId()).orElseThrow();
        assertThat(entregue.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(entregue.getProviderMessageId()).startsWith("log:");
    }

    @Test
    @DisplayName("sem pendentes o despacho fica ocioso e nao chama o canal")
    void despachoOcioso() {
        DispatchResult r = service.despacharPendentes();

        assertThat(r.ocioso()).isTrue();
        assertThat(canal.enviados).isEmpty();
    }

    @Test
    @DisplayName("alerta ja enviado nao e reenviado")
    void naoReenviaOqueJaFoi() {
        alertaPendente(AlertChannel.WHATSAPP);
        service.despacharPendentes();
        canal.limpar();

        DispatchResult r = service.despacharPendentes();

        assertThat(r.ocioso()).isTrue();
        assertThat(canal.enviados).isEmpty();
    }

    // ------------------------------------------------------------- falhas

    @Test
    @DisplayName("falha permanente vai direto a FAILED, sem gastar tentativa")
    void falhaPermanenteNaoRetenta() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);
        canal.resposta = DeliveryResult.falhaPermanente("numero invalido");

        DispatchResult r = service.despacharPendentes();

        assertThat(r.falhas()).isEqualTo(1);
        assertThat(r.retentar()).isZero();

        Alert falhado = alertas.findById(a.getId()).orElseThrow();
        assertThat(falhado.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(falhado.getErrorMessage()).contains("numero invalido");
        // Nao gastou tentativa: retentar nao adiantaria.
        assertThat(falhado.getAttempts()).isZero();
    }

    @Test
    @DisplayName("falha transitoria mantem PENDING e conta a tentativa")
    void falhaTransitoriaRetenta() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);
        canal.resposta = DeliveryResult.falhaTransitoria("HTTP 500 do provedor");

        DispatchResult r = service.despacharPendentes();

        assertThat(r.retentar()).isEqualTo(1);
        assertThat(r.falhas()).isZero();

        Alert pendente = alertas.findById(a.getId()).orElseThrow();
        assertThat(pendente.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(pendente.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("apos o limite de tentativas o alerta vira FAILED")
    void desisteAposOLimite() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);
        canal.resposta = DeliveryResult.falhaTransitoria("timeout");

        for (int i = 0; i < props.maxTentativas(); i++) {
            service.despacharPendentes();
        }

        Alert falhado = alertas.findById(a.getId()).orElseThrow();
        assertThat(falhado.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(falhado.getAttempts()).isEqualTo(props.maxTentativas());
        assertThat(falhado.getErrorMessage()).contains("desistindo apos");
    }

    @Test
    @DisplayName("retentativa que da certo apaga o erro anterior")
    void retentativaBemSucedida() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);
        canal.resposta = DeliveryResult.falhaTransitoria("rede instavel");
        service.despacharPendentes();

        canal.resposta = DeliveryResult.entregue("falso:2");
        DispatchResult r = service.despacharPendentes();

        assertThat(r.entregues()).isEqualTo(1);
        Alert entregue = alertas.findById(a.getId()).orElseThrow();
        assertThat(entregue.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(entregue.getErrorMessage()).isNull();
        assertThat(entregue.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("canal que lanca excecao nao derruba o despacho")
    void canalMalComportadoNaoDerruba() {
        Alert a = alertaPendente(AlertChannel.WHATSAPP);
        canal.explode = new IllegalStateException("canal quebrado");

        DispatchResult r = service.despacharPendentes();

        // Tratado como transitorio: pode ser defeito passageiro.
        assertThat(r.retentar()).isEqualTo(1);
        Alert pendente = alertas.findById(a.getId()).orElseThrow();
        assertThat(pendente.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(pendente.getErrorMessage()).contains("excecao nao tratada");
    }

    // ------------------------------------------------------------- lote

    @Test
    @DisplayName("varios pendentes sao entregues no mesmo despacho")
    void entregaEmLote() {
        Recipient outro = destinatarios.saveAndFlush(new Recipient("Amiga", "+5511922223333"));
        alertaPendente(AlertChannel.WHATSAPP);

        PriceObservation o = observacoes.findAll().get(0);
        Alert segundo = new Alert(monitor, o, outro, "✈️ Oportunidade: R$ 2.980");
        segundo.setChannel(AlertChannel.WHATSAPP);
        alertas.saveAndFlush(segundo);

        DispatchResult r = service.despacharPendentes();

        assertThat(r.reivindicados()).isEqualTo(2);
        assertThat(r.entregues()).isEqualTo(2);
        assertThat(alertas.countByStatus(AlertStatus.SENT)).isEqualTo(2);
    }

    @Test
    @DisplayName("uma entrega que falha nao impede as outras do lote")
    void falhaIsoladaNaoBloqueiaOLote() {
        Recipient outro = destinatarios.saveAndFlush(new Recipient("Amiga", "+5511933334444"));
        alertaPendente(AlertChannel.WHATSAPP);

        PriceObservation o = observacoes.findAll().get(0);
        // Canal sem implementacao registrada: falha permanente.
        Alert semCanal = new Alert(monitor, o, outro, "✈️ Oportunidade");
        semCanal.setChannel(AlertChannel.LOG);
        alertas.saveAndFlush(semCanal);

        DispatchResult r = service.despacharPendentes();

        assertThat(r.reivindicados()).isEqualTo(2);
        // O de WHATSAPP vai pelo canal falso, o de LOG pelo canal de log real.
        assertThat(r.entregues()).isEqualTo(2);
    }

    @Test
    @DisplayName("o canal ativo configurado e o que os alertas novos recebem")
    void canalAtivoConfigurado() {
        assertThat(service.canalAtivo()).isEqualTo(props.canal());
    }
}
