package com.flightmonitor.core.search.boundary.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WorkerClientConfig {

    /**
     * Cliente da camada 1 (varredura).
     *
     * <p>Cada camada ganha seu proprio {@link RestClient} porque o timeout de
     * leitura e definido na fabrica de requisicoes, e nao por chamada.
     */
    @Bean
    RestClient workerScanClient(WorkerProperties props) {
        return construir(props, props.scanTimeout());
    }

    /** Cliente da camada 2 (confirmacao), com timeout mais generoso. */
    @Bean
    RestClient workerConfirmClient(WorkerProperties props) {
        return construir(props, props.confirmTimeout());
    }

    private RestClient construir(WorkerProperties props, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                // HTTP/1.1 explicito. O HttpClient da JDK tenta HTTP/2 por padrao,
                // mas o worker roda em uvicorn, que so fala HTTP/1.1. Sem isto,
                // cada requisicao paga uma negociacao que sempre falha — e com
                // servidores mais rigorosos a conexao chega a ser cortada com
                // RST_STREAM. Descoberto na etapa E1.7.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
