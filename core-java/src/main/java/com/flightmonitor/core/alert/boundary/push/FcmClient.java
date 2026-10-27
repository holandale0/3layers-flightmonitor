package com.flightmonitor.core.alert.boundary.push;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Envia UMA mensagem a UM aparelho pela API HTTP v1 do FCM.
 *
 * <p>Separado do canal porque ha dois clientes: o despacho de alertas e o botao
 * "enviar teste" da tela, que precisa da mesma classificacao de erro sem criar
 * alerta nenhum.
 *
 * <p>Nunca lanca: devolve {@link Resposta} dizendo o que fazer em seguida.
 */
@Component
public class FcmClient {

    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);

    /**
     * Canal de notificacao do Android, criado pelo aplicativo. Precisa bater
     * com o id de la: com um id desconhecido, o Android 8+ entrega a mensagem
     * num canal generico, sem o som e a prioridade que o app configurou.
     */
    static final String CANAL_ANDROID = "alertas";

    private final PushProperties props;
    private final CredencialDoFirebase credencial;
    private final ObjectMapper json;
    private final RestClient client;

    public FcmClient(PushProperties props, CredencialDoFirebase credencial, ObjectMapper json) {
        this.props = props;
        this.credencial = credencial;
        this.json = json;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                // Versao declarada, e nao herdada — mesma licao do BUG-004 no
                // adaptador do WhatsApp.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.timeout());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configurado() {
        return credencial.configurada();
    }

    /** O que aconteceu com o envio, e por isso o que fazer com o aparelho. */
    public record Resposta(Tipo tipo, String id, String erro) {

        public enum Tipo {
            ENVIADO,
            /** O token nao vale mais: o aparelho deve ser desligado. */
            TOKEN_INVALIDO,
            /** Rede, cota, FCM fora do ar: vale tentar de novo. */
            TRANSITORIO,
            /** Credencial ou configuracao: retentar repetiria o mesmo erro. */
            PERMANENTE
        }

        static Resposta enviado(String id) {
            return new Resposta(Tipo.ENVIADO, id, null);
        }

        static Resposta falha(Tipo tipo, String erro) {
            return new Resposta(tipo, null, erro);
        }

        public boolean sucesso() {
            return tipo == Tipo.ENVIADO;
        }
    }

    /**
     * @param dados pares que o aplicativo le ao tocar na notificacao — o
     *        caminho da tela a abrir, por exemplo. So texto: o FCM recusa
     *        outros tipos em {@code data}
     */
    public Resposta enviar(String token, String titulo, String corpo, Map<String, String> dados) {
        if (!credencial.configurada()) {
            return Resposta.falha(Resposta.Tipo.PERMANENTE,
                    "canal PUSH sem conta de servico do Firebase: veja docs/GUIA-PUSH.md");
        }

        String acesso;
        try {
            acesso = credencial.tokenDeAcesso();
        } catch (IOException | RuntimeException e) {
            // O Google nao respondeu, ou recusou a chave. Os dois chegam aqui
            // iguais, e so o primeiro passa sozinho — mas tres tentativas
            // custam pouco, e desistir de cara perderia o alerta numa queda de
            // rede de um minuto.
            return Resposta.falha(Resposta.Tipo.TRANSITORIO,
                    "nao foi possivel obter o token de acesso do Google: " + e.getMessage());
        }

        try {
            String resposta = client.post()
                    .uri(props.enderecoDeEnvio(credencial.projeto()))
                    .header("Authorization", "Bearer " + acesso)
                    .header("Content-Type", "application/json")
                    .body(montar(token, titulo, corpo, dados))
                    .retrieve()
                    .body(String.class);
            return extrairId(resposta);

        } catch (RestClientResponseException e) {
            return classificar(e);
        } catch (RestClientException e) {
            return Resposta.falha(Resposta.Tipo.TRANSITORIO, "falha de rede: " + e.getMessage());
        }
    }

    private Map<String, Object> montar(
            String token, String titulo, String corpo, Map<String, String> dados) {
        return Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", titulo, "body", corpo),
                "data", dados,
                "android", Map.of(
                        // HIGH acorda o aparelho em modo soneca (Doze). NORMAL
                        // seria entregue na proxima janela de manutencao — que
                        // de madrugada pode ser horas depois, e oferta de
                        // passagem nao espera.
                        "priority", "HIGH",
                        "notification", Map.of("channel_id", CANAL_ANDROID))));
    }

    private Resposta extrairId(String corpo) {
        try {
            String id = json.readTree(corpo).path("name").asString(null);
            if (id != null && !id.isBlank()) {
                return Resposta.enviado(id);
            }
            return Resposta.falha(Resposta.Tipo.TRANSITORIO, "resposta 200 sem o nome da mensagem");
        } catch (RuntimeException e) {
            return Resposta.falha(Resposta.Tipo.TRANSITORIO, "resposta ilegivel: " + e.getMessage());
        }
    }

    /**
     * Traduz o erro do FCM em decisao.
     *
     * <p>O que importa e separar <b>token morto</b> de <b>problema nosso</b>. O
     * primeiro desliga um aparelho; o segundo, se fosse tratado como o
     * primeiro, desligaria TODOS — um erro de configuracao apagaria o cadastro
     * de quem recebe.
     */
    private Resposta classificar(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String codigo = null;
        String mensagem = null;
        try {
            JsonNode erro = json.readTree(e.getResponseBodyAsString()).path("error");
            mensagem = erro.path("message").asString(null);
            // O motivo especifico do FCM vem em details[], ao lado do status
            // generico do Google (NOT_FOUND, INVALID_ARGUMENT...).
            for (JsonNode d : erro.path("details")) {
                String c = d.path("errorCode").asString(null);
                if (c != null) {
                    codigo = c;
                }
            }
        } catch (RuntimeException ignorado) {
            // Corpo fora do formato; o status HTTP decide sozinho.
        }

        String detalhe = "HTTP %d%s: %s".formatted(
                status, codigo != null ? " " + codigo : "", mensagem != null ? mensagem : "sem detalhe");

        // UNREGISTERED: app desinstalado ou dados limpos. SENDER_ID_MISMATCH:
        // o token e de outro projeto do Firebase. Nos dois, nenhum envio futuro
        // chega a esse token.
        if ("UNREGISTERED".equals(codigo) || "SENDER_ID_MISMATCH".equals(codigo)
                || ("INVALID_ARGUMENT".equals(codigo) && falaDoToken(mensagem))) {
            log.warn("FCM recusou o token do aparelho — {}", detalhe);
            return Resposta.falha(Resposta.Tipo.TOKEN_INVALIDO, detalhe);
        }

        if (status == 429 || status >= 500
                || "QUOTA_EXCEEDED".equals(codigo) || "UNAVAILABLE".equals(codigo)
                || "INTERNAL".equals(codigo)) {
            log.warn("FCM devolveu erro transitorio — {}", detalhe);
            return Resposta.falha(Resposta.Tipo.TRANSITORIO, detalhe);
        }

        // 401 e 403 caem aqui: chave revogada, API do FCM desligada no projeto.
        log.error("FCM devolveu erro permanente — {}", detalhe);
        return Resposta.falha(Resposta.Tipo.PERMANENTE, detalhe);
    }

    /**
     * INVALID_ARGUMENT vale para token malformado E para payload malformado. So
     * o primeiro e culpa do aparelho; o segundo e nosso, e desligar o aparelho
     * por ele seria punir quem recebe por um defeito de quem envia.
     */
    private static boolean falaDoToken(String mensagem) {
        return mensagem != null && mensagem.toLowerCase().contains("registration token");
    }
}
