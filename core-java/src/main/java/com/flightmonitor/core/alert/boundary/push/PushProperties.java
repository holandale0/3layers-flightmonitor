package com.flightmonitor.core.alert.boundary.push;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do canal PUSH — Firebase Cloud Messaging (D-113).
 *
 * @param credenciais caminho do JSON da conta de servico do Firebase. E um
 *        <b>arquivo</b>, e nao uma variavel de ambiente: a chave privada tem
 *        quebras de linha, e colada no .env ela chega corrompida de um jeito
 *        que so aparece na hora de assinar. O projeto vem de dentro do arquivo
 * @param baseUrl endereco da API do FCM. Configuravel para os testes apontarem
 *        para um servidor falso
 * @param timeout conexao e leitura
 */
@ConfigurationProperties(prefix = "flightmonitor.push")
public record PushProperties(String credenciais, String baseUrl, Duration timeout) {

    public PushProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://fcm.googleapis.com" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }

    public boolean temCredenciais() {
        return credenciais != null && !credenciais.isBlank();
    }

    /** {baseUrl}/v1/projects/{projeto}/messages:send */
    public String enderecoDeEnvio(String projeto) {
        return "%s/v1/projects/%s/messages:send".formatted(baseUrl, projeto);
    }
}
