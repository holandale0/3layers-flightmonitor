package com.flightmonitor.core.alert.boundary.push;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

/**
 * Credencial lida do JSON da conta de servico, baixado do console do Firebase.
 *
 * <p>Carregada na subida, e nao a cada envio: o arquivo nao muda com o sistema
 * no ar, e um arquivo ilegivel precisa aparecer no log de partida — e nao so no
 * primeiro alerta, que pode chegar dias depois.
 *
 * <p>Sem arquivo, o sistema sobe igual. O canal PUSH recusa os envios com
 * falha permanente e mensagem clara, como o e-mail sem remetente.
 */
@Component
public class ContaDeServicoDoFirebase implements CredencialDoFirebase {

    private static final Logger log = LoggerFactory.getLogger(ContaDeServicoDoFirebase.class);

    /** O unico escopo necessario: enviar mensagem, e nada mais do projeto. */
    private static final List<String> ESCOPO =
            List.of("https://www.googleapis.com/auth/firebase.messaging");

    private final GoogleCredentials credencial;
    private final String projeto;

    public ContaDeServicoDoFirebase(PushProperties props) {
        ServiceAccountCredentials lida = props.temCredenciais() ? ler(props.credenciais()) : null;

        if (lida == null) {
            if (!props.temCredenciais()) {
                log.warn("canal PUSH sem credencial: defina FCM_CREDENCIAIS com o caminho do JSON "
                        + "da conta de servico. Envios serao recusados ate la");
            }
            this.credencial = null;
            this.projeto = null;
            return;
        }

        this.credencial = lida.createScoped(ESCOPO);
        this.projeto = lida.getProjectId();
        log.info("canal PUSH com a conta de servico {} do projeto {}", lida.getClientEmail(), projeto);
    }

    private static ServiceAccountCredentials ler(String caminho) {
        try (InputStream in = Files.newInputStream(Path.of(caminho))) {
            return ServiceAccountCredentials.fromStream(in);
        } catch (IOException | RuntimeException e) {
            // Nao derruba a subida: um push quebrado nao pode levar junto o
            // e-mail e o WhatsApp, que funcionam sem ele.
            log.error("canal PUSH: nao foi possivel ler a conta de servico em {}: {}",
                    caminho, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean configurada() {
        return credencial != null && projeto != null;
    }

    @Override
    public String projeto() {
        return projeto;
    }

    @Override
    public String tokenDeAcesso() throws IOException {
        // Renova so quando falta pouco para vencer; nas outras chamadas devolve
        // o token guardado, sem ir ao Google.
        credencial.refreshIfExpired();
        return credencial.getAccessToken().getTokenValue();
    }
}
