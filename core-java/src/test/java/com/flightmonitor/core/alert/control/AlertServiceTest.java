package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertStatus;
import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.Alert;

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

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.search.control.SearchOutcome;

import jakarta.persistence.EntityManager;

/**
 * Testa a regra que decide quando incomodar o usuario.
 *
 * <p>E a regra mais importante do produto: alertar demais treina o usuario a
 * ignorar a notificacao, e um sistema ignorado e pior que um desligado — da
 * falsa sensacao de cobertura.
 */
@SpringBootTest
class AlertServiceTest {

    @Autowired
    private AlertService service;

    @Autowired
    private AlertRepository alertas;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private RecipientRepository destinatarios;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private AlertProperties props;

    @Autowired
    private NotificationProperties notificacao;

    @Autowired
    private EntityManager em;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Monitor monitor;
    private final LocalDate ida = LocalDate.now().plusMonths(6);
    private final LocalDate volta = LocalDate.now().plusMonths(6).plusDays(12);

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        alertas.deleteAll();
        observacoes.deleteAll();
        monitores.deleteAll();
        destinatarios.deleteAll();
    }

    /**
     * Limpeza e preparo no MESMO metodo, de proposito.
     *
     * <p>Dois {@code @BeforeEach} separados nao tem ordem garantida pelo JUnit —
     * na primeira versao deste teste a limpeza rodou depois do preparo e apagou
     * o monitor recem-criado, quebrando 15 dos 16 testes.
     */
    @BeforeEach
    void preparar() {
        limparBanco();

        Recipient r = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511933332222"));

        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(10));
        m.setMaxPrice(new BigDecimal("3200.00"));
        m.addRecipient(r);
        monitor = monitores.saveAndFlush(m);
    }

    private PriceObservation observacao(String preco, LocalDate dataIda, LocalDate dataVolta) {
        PriceObservation o = new PriceObservation(
                monitor, "GRU", "LIS", dataIda, new BigDecimal(preco), PriceSource.FAST_FLIGHTS);
        o.setReturnDate(dataVolta);
        o.setCurrency("BRL");
        o.setAirline("TAP");
        o.setStops((short) 0);
        o.setConfirmed(true);
        return observacoes.saveAndFlush(o);
    }

    private SearchOutcome oportunidade(PriceObservation o) {
        return new SearchOutcome(
                monitor.getId(), 1, 1, o.getId(), o.getPrice(),
                true, false, false, false, List.of());
    }

    /**
     * Empurra os alertas existentes para tras, simulando cooldown vencido.
     *
     * <p>Via {@code JdbcTemplate} e nao {@code EntityManager}: a classe nao e
     * transacional (o servico gerencia as proprias transacoes), e uma query de
     * update pelo EntityManager exigiria transacao ativa.
     */
    private void envelhecerAlertas(int horas) {
        jdbc.update("update alert set created_at = created_at - make_interval(hours => ?)", horas);
        em.clear();
    }

    // ------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("primeira oportunidade gera alerta")
    void primeiroAlerta() {
        PriceObservation o = observacao("2980", ida, volta);

        AlertDecision d = service.avaliar(oportunidade(o));

        assertThat(d.alertar()).isTrue();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.ALERTADO);
        assertThat(alertas.findAll()).hasSize(1);
        assertThat(alertas.findAll().get(0).getStatus()).isEqualTo(AlertStatus.PENDING);
    }

    @Test
    @DisplayName("um alerta por destinatario ativo")
    void umAlertaPorDestinatario() {
        Recipient outro = destinatarios.saveAndFlush(new Recipient("Amiga", "+5511944443333"));
        Recipient inativo = new Recipient("Antigo", "+5511955554444");
        inativo.setActive(false);
        destinatarios.saveAndFlush(inativo);

        Monitor m = monitores.findByIdComDestinatarios(monitor.getId()).orElseThrow();
        m.addRecipient(outro);
        m.addRecipient(inativo);
        monitores.saveAndFlush(m);

        AlertDecision d = service.avaliar(oportunidade(observacao("2980", ida, volta)));

        assertThat(d.alertar()).isTrue();
        // 2 ativos; o inativo nao recebe.
        assertThat(alertas.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("a mensagem traz rota, datas, preco e o limite configurado")
    void mensagemFormatada() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));

        String msg = alertas.findAll().get(0).getMessage();

        assertThat(msg).contains("GRU → LIS");
        assertThat(msg).contains("2.980");
        assertThat(msg).contains("3.200");
        assertThat(msg).contains("voo direto");
        assertThat(msg).contains("TAP");
        assertThat(msg).contains("Lisboa");
        // Confirmado: nao deve ter o aviso de preco nao confirmado.
        assertThat(msg).doesNotContain("não confirmado");
    }

    // ------------------------------------------------------------ recusas

    @Test
    @DisplayName("sem oportunidade nao alerta")
    void semOportunidade() {
        SearchOutcome vazio = SearchOutcome.semOportunidade(monitor.getId(), 5, List.of());

        AlertDecision d = service.avaliar(vazio);

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.SEM_OPORTUNIDADE);
        assertThat(alertas.findAll()).isEmpty();
    }

    @Test
    @DisplayName("camada 2 degradada SEGURA o alerta")
    void camada2DegradadaNaoAlerta() {
        PriceObservation o = observacao("2980", ida, volta);
        SearchOutcome degradado = new SearchOutcome(
                monitor.getId(), 1, 1, o.getId(), o.getPrice(),
                false, true, false, false, List.of());

        AlertDecision d = service.avaliar(degradado);

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.SEM_CONFIRMACAO);
        assertThat(alertas.findAll()).isEmpty();
    }

    @Test
    @DisplayName("monitor sem destinatario ativo nao gera alerta")
    void semDestinatarios() {
        Monitor m = monitores.findByIdComDestinatarios(monitor.getId()).orElseThrow();
        m.getRecipients().clear();
        monitores.saveAndFlush(m);

        AlertDecision d = service.avaliar(oportunidade(observacao("2980", ida, volta)));

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.SEM_DESTINATARIOS);
    }

    // --------------------------------------------------------- anti-spam

    @Test
    @DisplayName("segundo alerta dentro do cooldown e bloqueado")
    void cooldownBloqueia() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));

        AlertDecision d = service.avaliar(oportunidade(observacao("2500", ida, volta)));

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.DENTRO_DO_COOLDOWN);
        assertThat(alertas.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("passado o cooldown, queda insuficiente ainda bloqueia")
    void quedaInsuficienteBloqueia() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));
        envelhecerAlertas((int) props.cooldown().toHours() + 1);

        // 2% de queda: abaixo do minimo de 5%.
        AlertDecision d = service.avaliar(oportunidade(observacao("2920", ida, volta)));

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.QUEDA_INSUFICIENTE);
        assertThat(d.detalhe()).contains("%");
        assertThat(alertas.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("queda acima do minimo re-alerta")
    void quedaSuficienteRealerta() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));
        envelhecerAlertas((int) props.cooldown().toHours() + 1);

        // ~10% de queda.
        AlertDecision d = service.avaliar(oportunidade(observacao("2680", ida, volta)));

        assertThat(d.alertar()).isTrue();
        assertThat(alertas.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("preco que subiu nunca re-alerta")
    void precoQueSubiuNaoRealerta() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));
        envelhecerAlertas((int) props.cooldown().toHours() + 1);

        AlertDecision d = service.avaliar(oportunidade(observacao("3100", ida, volta)));

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.QUEDA_INSUFICIENTE);
    }

    @Test
    @DisplayName("datas ainda nao alertadas passam pela regra de queda")
    void datasNovasAlertam() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));
        envelhecerAlertas((int) props.cooldown().toHours() + 1);

        // Mesmo preco, mas outra combinacao de datas: e uma oferta diferente.
        LocalDate outraIda = ida.plusDays(3);
        AlertDecision d = service.avaliar(oportunidade(observacao("2980", outraIda, volta)));

        assertThat(d.alertar()).isTrue();
        assertThat(d.detalhe()).contains("datas ainda nao alertadas");
    }

    @Test
    @DisplayName("alerta FAILED nao bloqueia um novo: o usuario nunca o recebeu")
    void alertaFalhadoNaoBloqueia() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));

        Alert falhado = alertas.findAll().get(0);
        falhado.marcarFalha("numero invalido");
        alertas.saveAndFlush(falhado);

        AlertDecision d = service.avaliar(oportunidade(observacao("2970", ida, volta)));

        assertThat(d.alertar()).isTrue();
        assertThat(d.detalhe()).contains("primeiro alerta");
    }

    @Test
    @DisplayName("o cooldown vale entre datas diferentes tambem")
    void cooldownValeEntreDatasDiferentes() {
        service.avaliar(oportunidade(observacao("2980", ida, volta)));

        // Outra data, dentro do cooldown: nao manda rajada.
        AlertDecision d = service.avaliar(oportunidade(observacao("2100", ida.plusDays(5), volta)));

        assertThat(d.alertar()).isFalse();
        assertThat(d.motivo()).isEqualTo(AlertDecision.Motivo.DENTRO_DO_COOLDOWN);
    }

    @Test
    @DisplayName("o alerta aponta para a observacao que o originou")
    void alertaRastreiaAObservacao() {
        PriceObservation o = observacao("2980", ida, volta);

        service.avaliar(oportunidade(o));

        Alert a = alertas.findAll().get(0);
        assertThat(a.getPriceObservation().getId()).isEqualTo(o.getId());
        assertThat(a.getMonitor().getId()).isEqualTo(monitor.getId());
        assertThat(a.getRecipient()).isNotNull();
        // O canal gravado e o configurado, nao um valor fixo: trocar o canal
        // depois nao pode reescrever como um alerta antigo foi entregue.
        assertThat(a.getChannel()).isEqualTo(notificacao.canal());
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(a.getAttempts()).isZero();
    }

    @Test
    @DisplayName("alertas do mesmo lote compartilham o instante, mas sao linhas distintas")
    void loteDeAlertas() {
        Recipient outro = destinatarios.saveAndFlush(new Recipient("Amiga", "+5511966665555"));
        Monitor m = monitores.findByIdComDestinatarios(monitor.getId()).orElseThrow();
        m.addRecipient(outro);
        monitores.saveAndFlush(m);

        service.avaliar(oportunidade(observacao("2980", ida, volta)));

        List<Alert> criados = alertas.findAll();
        assertThat(criados).hasSize(2);
        // O clock_timestamp da migration V2 garante instantes distintos mesmo
        // dentro da mesma transacao — foi o BUG-002.
        assertThat(criados).extracting(Alert::getId).doesNotHaveDuplicates();
        assertThat(criados).extracting(Alert::getCreatedAt).doesNotContainNull();
    }

    @Test
    @DisplayName("mensagem avisa quando o preco nao foi confirmado")
    void mensagemAvisaPrecoNaoConfirmado() {
        PriceObservation o = observacao("2980", ida, volta);
        o.setConfirmed(false);
        observacoes.saveAndFlush(o);

        service.avaliar(oportunidade(o));

        assertThat(alertas.findAll().get(0).getMessage()).contains("não confirmado");
    }
}
