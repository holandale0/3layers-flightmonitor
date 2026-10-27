package com.flightmonitor.core.alert.boundary.push;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.recipient.entity.PushDevice;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;

/**
 * Manda uma notificacao de teste a um aparelho.
 *
 * <p>Existe porque, sem ele, a unica forma de saber se o push funciona e
 * esperar uma passagem cair abaixo do teto — que pode levar semanas, e e
 * justamente quando o alerta precisava ter chegado. Mesmo motivo do teste de
 * envio da configuracao do WhatsApp.
 *
 * <p>Nao cria alerta: e um diagnostico, nao uma oferta, e nao pode entrar no
 * anti-spam nem no historico de entregas.
 */
@RestController
public class PushTesteController {

    private final FcmClient fcm;
    private final PushDeviceRepository aparelhos;

    public PushTesteController(FcmClient fcm, PushDeviceRepository aparelhos) {
        this.fcm = fcm;
        this.aparelhos = aparelhos;
    }

    /** O resultado vai no corpo, com 200: a chamada funcionou, quem falhou foi o envio. */
    public record ResultadoDoTeste(boolean enviado, String erro) {
    }

    @PostMapping("/api/push-devices/{id}/teste")
    public ResultadoDoTeste testar(@PathVariable Long id) {
        PushDevice aparelho = aparelhos.findById(id)
                .orElseThrow(() -> new NotFoundException("Aparelho", id));

        FcmClient.Resposta r = fcm.enviar(
                aparelho.getToken(),
                "✈ Teste do Monitor de Passagens",
                "Se você está lendo isto, os alertas vão chegar neste aparelho.",
                Map.of("caminho", "/destinatarios"));

        if (r.tipo() == FcmClient.Resposta.Tipo.TOKEN_INVALIDO) {
            aparelhos.desativar(aparelho.getId(), r.erro());
        }
        return new ResultadoDoTeste(r.sucesso(), r.erro());
    }
}
