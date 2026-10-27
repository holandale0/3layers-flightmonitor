package com.flightmonitor.core.alert.boundary.push;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.flightmonitor.core.alert.control.AlertMessageFormatter;
import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.control.NotificationChannel;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.recipient.entity.PushDevice;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;

/**
 * Entrega o alerta como notificacao no aplicativo Android — D-113.
 *
 * <h2>Um alerta, varios aparelhos</h2>
 *
 * O alerta e de um destinatario, e o destinatario pode ter celular e tablet.
 * Vai para todos os aparelhos ativos dele, e conta como entregue se <b>algum</b>
 * recebeu: a pergunta que o alerta responde e "a pessoa foi avisada?", e nao
 * "todos os aparelhos foram". O que falhou em cada um fica no log, e o token
 * morto desliga o aparelho com o motivo gravado.
 *
 * <p>Consequencia aceita: se o celular recebeu e o tablet estava com a rede
 * fora, o tablet nao recebe numa retentativa — retentar reenviaria tambem ao
 * celular, e notificacao repetida e o defeito que o despacho inteiro foi
 * desenhado para evitar.
 *
 * <h2>SENT, e nao ACCEPTED</h2>
 *
 * O FCM nao avisa quando a notificacao aparece na tela, entao nao ha webhook
 * para esperar. {@link #confirmacaoAssincrona()} fica no padrao {@code false}, e
 * SENT aqui quer dizer "o Google aceitou para entrega" — como no e-mail, menos
 * do que o WhatsApp informa, e sem fingir o contrario.
 */
@Component
public class PushNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);

    /** *negrito* e _italico_ do WhatsApp, que na notificacao virariam simbolo solto. */
    private static final Pattern NEGRITO = Pattern.compile("\\*([^*\\n]+)\\*");
    // O _ de fechamento e o primeiro que nao vem colado a uma letra: assim o
    // rotulo "viagem_de_ferias" sobrevive inteiro dentro do italico.
    private static final Pattern ITALICO = Pattern.compile("(?<![\\p{L}\\d])_(.+?)_(?![\\p{L}\\d])");

    private final FcmClient fcm;
    private final PushDeviceRepository aparelhos;

    public PushNotificationChannel(FcmClient fcm, PushDeviceRepository aparelhos) {
        this.fcm = fcm;
        this.aparelhos = aparelhos;
    }

    @Override
    public AlertChannel canal() {
        return AlertChannel.PUSH;
    }

    @Override
    public DeliveryResult enviar(Alert alerta) {
        if (!fcm.configurado()) {
            return DeliveryResult.falhaPermanente(
                    "canal PUSH sem conta de servico do Firebase (FCM_CREDENCIAIS): veja docs/GUIA-PUSH.md");
        }
        if (alerta.getRecipient() == null) {
            return DeliveryResult.falhaPermanente("alerta sem destinatario");
        }

        List<PushDevice> destinos = aparelhos.findByRecipientIdAndActiveTrue(alerta.getRecipient().getId());
        if (destinos.isEmpty()) {
            // Permanente: nenhuma retentativa instala o aplicativo.
            return DeliveryResult.falhaPermanente(
                    "destinatario '%s' nao tem aparelho ativo: abra o aplicativo e toque em "
                            .formatted(alerta.getRecipient().getName())
                            + "\"Receber neste aparelho\" na tela de destinatarios");
        }

        String titulo = AlertMessageFormatter.titulo(alerta.getPriceObservation());
        String corpo = corpo(alerta.getMessage());
        Map<String, String> dados = dados(alerta);

        String primeiroId = null;
        List<String> erros = new ArrayList<>();
        boolean algumTransitorio = false;

        for (PushDevice aparelho : destinos) {
            FcmClient.Resposta r = fcm.enviar(aparelho.getToken(), titulo, corpo, dados);

            switch (r.tipo()) {
                case ENVIADO -> {
                    if (primeiroId == null) {
                        primeiroId = r.id();
                    }
                }
                case TOKEN_INVALIDO -> {
                    aparelhos.desativar(aparelho.getId(), r.erro());
                    erros.add(aparelho.getName() + ": " + r.erro());
                }
                case TRANSITORIO -> {
                    algumTransitorio = true;
                    erros.add(aparelho.getName() + ": " + r.erro());
                }
                case PERMANENTE -> erros.add(aparelho.getName() + ": " + r.erro());
            }
        }

        if (primeiroId != null) {
            if (!erros.isEmpty()) {
                log.warn("alerta {} entregue por push, mas nao a todos os aparelhos: {}",
                        alerta.getId(), erros);
            }
            log.info("alerta {} enviado por push a {} aparelho(s)", alerta.getId(), destinos.size() - erros.size());
            // O id do primeiro basta: e para achar a mensagem no console do
            // Firebase, e o indice unico de provider_message_id nao aceitaria
            // uma lista.
            return DeliveryResult.entregue(primeiroId);
        }

        String resumo = String.join("; ", erros);
        return algumTransitorio
                ? DeliveryResult.falhaTransitoria(resumo)
                : DeliveryResult.falhaPermanente(resumo);
    }

    /**
     * A mensagem do alerta, sem a marcacao do WhatsApp e sem o cabecalho.
     *
     * <p>O texto e o mesmo dos outros canais, gravado no alerta. O cabecalho
     * "Oportunidade encontrada" sai porque o titulo da notificacao ja diz rota
     * e preco, e na notificacao recolhida so cabe a primeira linha do corpo —
     * que precisa ser informacao, e nao repeticao.
     */
    static String corpo(String mensagem) {
        if (mensagem == null) {
            return "";
        }
        String semMarcacao = ITALICO.matcher(NEGRITO.matcher(mensagem).replaceAll("$1")).replaceAll("$1");
        return semMarcacao.replaceFirst("^\\S*\\s*Oportunidade encontrada\\s*", "").strip();
    }

    /**
     * O que o aplicativo usa ao tocar na notificacao: a tela de historico do
     * monitor, que e onde a oferta faz sentido — com o grafico e as outras
     * datas ao lado.
     */
    private static Map<String, String> dados(Alert alerta) {
        String caminho = alerta.getMonitor() != null && alerta.getMonitor().getId() != null
                ? "/monitores/" + alerta.getMonitor().getId()
                : "/";
        return Map.of(
                "caminho", caminho,
                "alertaId", String.valueOf(alerta.getId()));
    }
}
