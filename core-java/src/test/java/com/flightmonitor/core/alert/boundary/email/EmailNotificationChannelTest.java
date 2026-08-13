package com.flightmonitor.core.alert.boundary.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;

/**
 * O canal de e-mail contra um SMTP de mentira.
 *
 * <p>O GreenMail sobe um servidor SMTP em memoria, numa porta alta. Nenhum teste
 * manda e-mail de verdade — mesma disciplina dos dubles das fontes externas
 * (secao 9 do plano). E, diferente do WhatsApp, aqui isso e possivel: nao ha
 * template para aprovar nem conta de terceiro para depender.
 */
class EmailNotificationChannelTest {

    @RegisterExtension
    static final GreenMailExtension SMTP =
            new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(true);

    private EmailNotificationChannel canalApontandoParaOFalso() {
        JavaMailSenderImpl remetente = new JavaMailSenderImpl();
        remetente.setHost("127.0.0.1");
        remetente.setPort(SMTP.getSmtp().getPort());
        return new EmailNotificationChannel(
                remetente, new EmailProperties("sistema@exemplo.com", "Monitor de Passagens"));
    }

    private Alert alertaDeTeste(String emailDoDestinatario) {
        Recipient destinatario = new Recipient("Leonardo", "+5511999998888", emailDoDestinatario);

        PriceObservation oferta = new PriceObservation();
        ReflectionTestUtils.setField(oferta, "origin", "GRU");
        ReflectionTestUtils.setField(oferta, "destination", "SSA");
        ReflectionTestUtils.setField(oferta, "price", new BigDecimal("1401.00"));
        ReflectionTestUtils.setField(oferta, "currency", "BRL");
        ReflectionTestUtils.setField(oferta, "departureDate", LocalDate.of(2026, 9, 22));

        Alert alerta = new Alert();
        ReflectionTestUtils.setField(alerta, "id", 1728L);
        ReflectionTestUtils.setField(alerta, "recipient", destinatario);
        ReflectionTestUtils.setField(alerta, "priceObservation", oferta);
        ReflectionTestUtils.setField(alerta, "message",
                "Oportunidade encontrada\nGRU -> SSA\nR$ 1.401,00");
        return alerta;
    }

    @Test
    @DisplayName("declara o canal EMAIL")
    void declaraOCanal() {
        assertThat(canalApontandoParaOFalso().canal()).isEqualTo(AlertChannel.EMAIL);
    }

    @Test
    @DisplayName("SMTP aceitou e' o fim da historia: nao ha confirmacao posterior")
    void naoTemConfirmacaoAssincrona() {
        // O contrario do WhatsApp, que devolve true e espera webhook. Aqui um
        // true deixaria todo alerta preso em ACCEPTED, esperando um aviso que
        // nunca viria — foi o modo de falha que o BUG-007 ensinou a temer.
        assertThat(canalApontandoParaOFalso().confirmacaoAssincrona()).isFalse();
    }

    @Test
    @DisplayName("entrega o alerta e devolve o Message-ID")
    void entregaEDevolveOId() throws Exception {
        DeliveryResult resultado =
                canalApontandoParaOFalso().enviar(alertaDeTeste("leo@exemplo.com"));

        assertThat(resultado.sucesso()).isTrue();
        // O Message-ID e o unico identificador que o SMTP oferece — nao ha
        // equivalente do wamid da Meta, e e por ele que se rastreia nos logs.
        assertThat(resultado.providerMessageId()).isNotBlank().contains("@");

        MimeMessage[] recebidas = SMTP.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        assertThat(recebidas[0].getAllRecipients()[0]).hasToString("leo@exemplo.com");
        assertThat(recebidas[0].getContent().toString()).contains("Oportunidade encontrada");
    }

    @Test
    @DisplayName("o remetente leva nome exibido, e nao so o endereco cru")
    void remetenteComNome() throws Exception {
        canalApontandoParaOFalso().enviar(alertaDeTeste("leo@exemplo.com"));

        // Sem o nome, a caixa de entrada mostra "sistema@exemplo.com", que nao
        // diz nada a quem recebe.
        String de = SMTP.getReceivedMessages()[0].getFrom()[0].toString();
        assertThat(de).contains("Monitor de Passagens").contains("sistema@exemplo.com");
    }

    @Nested
    @DisplayName("o assunto")
    class OAssunto {

        @Test
        @DisplayName("carrega rota e preco, para o Gmail nao agrupar tudo numa thread")
        void variaPorAlerta() throws Exception {
            canalApontandoParaOFalso().enviar(alertaDeTeste("leo@exemplo.com"));

            String assunto = SMTP.getReceivedMessages()[0].getSubject();

            // Assunto fixo faria o quinto alerta aparecer colapsado dentro de
            // uma thread velha — o oposto do que um monitor precisa (D-098).
            assertThat(assunto).contains("GRU").contains("SSA").contains("1.401");
        }

        @Test
        @DisplayName("e legivel na notificacao do celular, sem abrir o e-mail")
        void cabeNaNotificacao() throws Exception {
            canalApontandoParaOFalso().enviar(alertaDeTeste("leo@exemplo.com"));

            String assunto = SMTP.getReceivedMessages()[0].getSubject();

            // Para um sistema que existe para avisar, a tela da notificacao e a
            // que mais importa: ~60 caracteres e o que costuma sobreviver nela.
            assertThat(assunto).hasSizeLessThan(60);
        }
    }

    @Nested
    @DisplayName("quando nao da para enviar")
    class QuandoNaoDaParaEnviar {

        @Test
        @DisplayName("destinatario sem e-mail e' falha PERMANENTE")
        void semEmailCadastrado() {
            DeliveryResult resultado = canalApontandoParaOFalso().enviar(alertaDeTeste(null));

            assertThat(resultado.sucesso()).isFalse();
            // Permanente porque nenhuma retentativa faz um e-mail aparecer no
            // cadastro. Transitoria gastaria as tres tentativas para nada.
            assertThat(resultado.transitorio()).isFalse();
            assertThat(resultado.erro()).contains("Leonardo").contains("nao tem e-mail");
        }

        @Test
        @DisplayName("sem remetente configurado, recusa com mensagem que diz o que fazer")
        void semRemetenteConfigurado() {
            EmailNotificationChannel canal = new EmailNotificationChannel(
                    new JavaMailSenderImpl(), new EmailProperties(null, null));

            DeliveryResult resultado = canal.enviar(alertaDeTeste("leo@exemplo.com"));

            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.transitorio()).isFalse();
            assertThat(resultado.erro()).contains("remetente");
        }

        @Test
        @DisplayName("servidor fora do ar e' falha TRANSITORIA")
        void servidorForaDoAr() {
            JavaMailSenderImpl morto = new JavaMailSenderImpl();
            morto.setHost("127.0.0.1");
            // Porta onde nao ha nada escutando: simula SMTP fora do ar.
            morto.setPort(1);
            EmailNotificationChannel canal = new EmailNotificationChannel(
                    morto, new EmailProperties("sistema@exemplo.com", null));

            DeliveryResult resultado = canal.enviar(alertaDeTeste("leo@exemplo.com"));

            assertThat(resultado.sucesso()).isFalse();
            // Transitoria: o servidor pode voltar, e retentar faz sentido. E a
            // mesma distincao que o adaptador do WhatsApp faz entre 5xx e 4xx.
            assertThat(resultado.transitorio()).isTrue();
        }

        @Test
        @DisplayName("alerta sem destinatario nao explode")
        void semDestinatario() {
            Alert alerta = alertaDeTeste("leo@exemplo.com");
            ReflectionTestUtils.setField(alerta, "recipient", null);

            DeliveryResult resultado = canalApontandoParaOFalso().enviar(alerta);

            // O contrato do NotificationChannel proibe lancar excecao: o
            // despachante e que decide o que fazer com a falha.
            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.transitorio()).isFalse();
        }
    }
}
