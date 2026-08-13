package com.flightmonitor.core.alert.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.control.NotificationChannel;
import com.flightmonitor.core.alert.control.NotificationProperties;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * O indicador que diz qual canal esta ativo.
 *
 * <p>Existe porque a tela de destinatarios precisa avisar quem <b>nao vai ser
 * alcancado</b> pelo canal em uso — e sem este indicador esse aviso nao teria
 * como existir. A primeira versao da tela lia
 * {@code components.notificacao.canal} de um indicador que <b>nao existia</b>: o
 * aviso nunca apareceria, e ninguem perceberia.
 */
class NotificacaoHealthIndicatorTest {

    /** Canal de mentira, so para dizer qual enum atende. */
    private record CanalFalso(AlertChannel canal, boolean assincrono) implements NotificationChannel {
        @Override
        public boolean confirmacaoAssincrona() {
            return assincrono;
        }

        @Override
        public DeliveryResult enviar(Alert alerta) {
            return DeliveryResult.entregue("falso");
        }
    }

    private NotificacaoHealthIndicator indicador(AlertChannel ativo, NotificationChannel... canais) {
        return new NotificacaoHealthIndicator(
                new NotificationProperties(ativo, 3, 50),
                List.of(canais));
    }

    @Test
    @DisplayName("diz qual canal esta ativo")
    void informaOCanalAtivo() {
        Health saude = indicador(
                        AlertChannel.EMAIL,
                        new CanalFalso(AlertChannel.EMAIL, false),
                        new CanalFalso(AlertChannel.LOG, false))
                .health();

        assertThat(saude.getStatus()).isEqualTo(Status.UP);
        assertThat(saude.getDetails()).containsEntry("canal", "EMAIL");
        assertThat(saude.getDetails().get("disponiveis")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactly("EMAIL", "LOG");
    }

    @Test
    @DisplayName("expoe se o alerta espera confirmacao externa")
    void informaSeEsperaWebhook() {
        // A diferenca visivel no painel: no WhatsApp o alerta para em ACCEPTED
        // ate o webhook chegar; no e-mail, SENT e o fim da linha.
        Health whatsapp = indicador(AlertChannel.WHATSAPP,
                new CanalFalso(AlertChannel.WHATSAPP, true)).health();
        Health email = indicador(AlertChannel.EMAIL,
                new CanalFalso(AlertChannel.EMAIL, false)).health();

        assertThat(whatsapp.getDetails()).containsEntry("confirmacaoAssincrona", true);
        assertThat(email.getDetails()).containsEntry("confirmacaoAssincrona", false);
    }

    @Test
    @DisplayName("canal configurado sem implementacao derruba o status")
    void canalSemImplementacaoEhDown() {
        // Este e um erro de verdade: o sistema acha que vai avisar por um canal
        // que nao existe, e todo alerta ficaria pendente para sempre.
        Health saude = indicador(AlertChannel.WHATSAPP,
                new CanalFalso(AlertChannel.LOG, false)).health();

        assertThat(saude.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("rodar em LOG e' saudavel, e nao doenca")
    void logNaoDerrubaOStatus() {
        // E o padrao de quem acabou de clonar o projeto. Marcar como DOWN faria
        // o /health mentir para quem esta so experimentando.
        Health saude = indicador(AlertChannel.LOG,
                new CanalFalso(AlertChannel.LOG, false)).health();

        assertThat(saude.getStatus()).isEqualTo(Status.UP);
        assertThat(saude.getDetails()).containsEntry("canal", "LOG");
    }
}
