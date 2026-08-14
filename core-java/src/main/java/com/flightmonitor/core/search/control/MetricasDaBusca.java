package com.flightmonitor.core.search.control;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * Metricas da varredura — etapa E4.3.
 *
 * <h2>Por que estas metricas, e nao outras</h2>
 *
 * O <b>BUG-014</b> definiu o que precisava ser medido. A camada 2 inteira ficou
 * indisponivel por <b>seis semanas</b> e ninguem percebeu: nao houve erro, nao
 * houve log vermelho. A cadeia de providers trata fonte ausente como degradacao
 * — que e o comportamento certo para uma fonte fragil — e o sintoma foi o
 * sistema parar de alertar, em silencio.
 *
 * <p>Nenhuma metrica de infraestrutura teria pego isso: CPU, memoria e latencia
 * estavam perfeitas. O que teria pego e {@code camada2.confirmacoes} zerado
 * enquanto {@code busca.execucoes} continuava subindo.
 *
 * <p>Por isso o que se mede aqui e <b>resultado de negocio</b>, e nao saude de
 * processo: quantas varreduras aconteceram, quantas confirmaram preco, quantas
 * viraram alerta. A pergunta que estas metricas respondem e "o sistema ainda
 * esta fazendo o que promete?", que e diferente de "o sistema esta no ar?".
 */
@Component
public class MetricasDaBusca {

    /** Uma varredura terminou. O rotulo diz em que ela deu. */
    public enum Desfecho {
        /** A fonte falhou: nao houve varredura de verdade. */
        FALHOU,
        /** Varreu, mas nenhum preco ficou abaixo do teto. */
        SEM_CANDIDATO,
        /** Havia candidato, e a camada 2 nao confirmou. */
        NAO_CONFIRMADO,
        /** Candidato confirmado: virou oportunidade real. */
        COM_OPORTUNIDADE
    }

    private final MeterRegistry registro;
    private final Timer duracao;

    public MetricasDaBusca(MeterRegistry registro) {
        this.registro = registro;
        this.duracao = Timer.builder("flightmonitor.busca.duracao")
                .description("Tempo de uma varredura completa de um monitor")
                .register(registro);
    }

    public void registrar(SearchOutcome resultado, Duration tempo) {
        duracao.record(tempo);

        Counter.builder("flightmonitor.busca.execucoes")
                .description("Varreduras concluidas, por desfecho")
                .tag("desfecho", desfecho(resultado).name().toLowerCase())
                .register(registro)
                .increment();

        if (resultado.observacoesGravadas() > 0) {
            Counter.builder("flightmonitor.observacoes.gravadas")
                    .description("Precos gravados no historico")
                    .register(registro)
                    .increment(resultado.observacoesGravadas());
        }

        registrarCamada2(resultado);
    }

    /**
     * A metrica que teria pego o BUG-014.
     *
     * <p>{@code confirmou} parado em zero enquanto {@code indisponivel} sobe e a
     * assinatura exata daquele bug — e ela aparece <b>na primeira varredura</b>,
     * nao em seis semanas.
     */
    private void registrarCamada2(SearchOutcome resultado) {
        if (resultado.falhou() || resultado.candidatosAbaixoDoTeto() == 0) {
            // Sem candidato nao se consulta a camada 2. Contar como "nao
            // confirmou" aqui poluiria a metrica com dias sem promocao, que sao
            // a maioria — e o sinal que importa sumiria no ruido.
            return;
        }

        String resultadoDaCamada;
        if (resultado.camada2Degradada()) {
            resultadoDaCamada = "indisponivel";
        } else if (resultado.candidatoIlusorio()) {
            resultadoDaCamada = "ilusorio";
        } else if (resultado.confirmada()) {
            resultadoDaCamada = "confirmou";
        } else {
            resultadoDaCamada = "recusou";
        }

        Counter.builder("flightmonitor.camada2.consultas")
                .description("Confirmacoes tentadas na camada 2, por resultado")
                .tag("resultado", resultadoDaCamada)
                .register(registro)
                .increment();
    }

    private static Desfecho desfecho(SearchOutcome resultado) {
        if (resultado.falhou()) {
            return Desfecho.FALHOU;
        }
        if (resultado.candidatosAbaixoDoTeto() == 0) {
            return Desfecho.SEM_CANDIDATO;
        }
        return resultado.temOportunidade() ? Desfecho.COM_OPORTUNIDADE : Desfecho.NAO_CONFIRMADO;
    }

    /** Uma decisao de alerta foi tomada. O rotulo e o motivo dela. */
    public void registrarDecisao(String motivo, boolean alertou) {
        Counter.builder("flightmonitor.alerta.decisoes")
                .description("Decisoes de alerta, por motivo")
                .tag("motivo", motivo.toLowerCase())
                .tag("alertou", String.valueOf(alertou))
                .register(registro)
                .increment();
    }
}
