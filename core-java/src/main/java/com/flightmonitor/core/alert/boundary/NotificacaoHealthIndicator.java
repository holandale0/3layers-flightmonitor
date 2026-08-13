package com.flightmonitor.core.alert.boundary;

import com.flightmonitor.core.alert.control.NotificationChannel;
import com.flightmonitor.core.alert.control.NotificationProperties;
import com.flightmonitor.core.alert.entity.AlertChannel;

import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Expoe qual canal de notificacao esta ativo em {@code /actuator/health}.
 *
 * <h2>Por que isto existe</h2>
 *
 * O canal ativo e a unica configuracao do sistema que muda o que o usuario
 * <b>recebe</b>, e ate aqui ela so era visivel numa linha de log da subida.
 *
 * <p>Foi a tela de destinatarios que cobrou: para avisar que alguem cadastrado
 * <b>nao vai ser alcancado</b> — um destinatario so com e-mail, com o sistema em
 * {@code WHATSAPP} — o painel precisa saber qual canal esta ativo. Sem isso o
 * aviso nao teria como existir, e o alerta falharia em silencio: envio vira
 * falha permanente, e a pessoa nunca sabe que perdeu a passagem.
 *
 * <h2>O status nao cai por causa disto</h2>
 *
 * Canal sem credencial e {@code UP} com detalhe, e nao {@code DOWN}. Rodar em
 * {@code LOG} e um modo legitimo de operacao — inclusive o padrao de quem acabou
 * de clonar o projeto. Marcar isso como doenca faria o {@code /health} mentir
 * para quem esta so experimentando.
 */
@Component("notificacao")
public class NotificacaoHealthIndicator implements HealthIndicator {

    private final NotificationProperties props;
    private final List<NotificationChannel> canais;

    public NotificacaoHealthIndicator(
            NotificationProperties props, List<NotificationChannel> canais) {
        this.props = props;
        this.canais = canais;
    }

    @Override
    public Health health() {
        AlertChannel ativo = props.canal();

        boolean implementado = canais.stream().anyMatch(c -> c.canal() == ativo);

        Health.Builder saude = implementado ? Health.up() : Health.down();

        return saude
                .withDetail("canal", ativo.name())
                .withDetail("disponiveis", canais.stream().map(c -> c.canal().name()).sorted().toList())
                // Quem le o painel precisa saber se o alerta vai esperar
                // confirmacao externa: no WhatsApp o alerta para em ACCEPTED ate
                // o webhook chegar; no e-mail, SENT e o fim da linha.
                .withDetail("confirmacaoAssincrona", canais.stream()
                        .filter(c -> c.canal() == ativo)
                        .anyMatch(NotificationChannel::confirmacaoAssincrona))
                .build();
    }
}
