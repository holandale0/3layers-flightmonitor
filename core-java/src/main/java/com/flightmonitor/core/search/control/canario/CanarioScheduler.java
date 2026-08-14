package com.flightmonitor.core.search.control.canario;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda o canario de tempos em tempos e deixa o resultado visivel — etapa E4.5.
 *
 * <h2>Por que uma rotina agendada, e nao um teste</h2>
 *
 * O que se quer detectar nao acontece quando alguem roda a suite: acontece
 * quando o Google muda o formato, num dia qualquer, sem aviso. Um teste so pega
 * isso se alguem estiver olhando; uma rotina agendada pega sozinha.
 *
 * <p>E deliberadamente <b>fora do CI</b>: depende de rede, consome cota e e
 * intrinsecamente instavel. Canario vermelho e informacao; canario no CI seria
 * ruido que treina todo mundo a ignorar falha (secao 9 do PLANO-DE-ACAO).
 *
 * <h2>Como ele "avisa"</h2>
 *
 * Por <b>log em ERROR, metrica e saude</b> — e nao pelo canal de alerta. O
 * template do WhatsApp aprovado pela Meta diz "encontrei uma passagem dentro do
 * preco que voce definiu"; usa-lo para avisar que uma biblioteca mudou de
 * formato seria mentir para o destinatario e, provavelmente, perder a aprovacao
 * do template.
 *
 * <p>Transformar o canario numa mensagem exige um <b>segundo formato</b> de
 * mensagem, com aprovacao propria — decisao que pertence a E4.4, quando existir
 * um ambiente rodando de forma continua para receber esse aviso.
 *
 * <h2>Uma vez por dia</h2>
 *
 * Formato de API nao muda de hora em hora, e cada execucao gasta cota de duas
 * fontes gratuitas. Rodar de minuto em minuto nao antecipa a descoberta em nada
 * util e aproxima o bloqueio por excesso de requisicoes (RISCO-004).
 */
@Component
@ConditionalOnProperty(name = "flightmonitor.canario.enabled", havingValue = "true")
public class CanarioScheduler {

    private static final Logger log = LoggerFactory.getLogger(CanarioScheduler.class);

    private final CanarioPort canario;

    /**
     * 1 saudavel, 0 com problema, -1 nao consultado.
     *
     * <p>O -1 e o valor que mais importa: sem ele, worker fora do ar apareceria
     * como fonte quebrada, e o alarme mandaria olhar o lugar errado.
     */
    private final AtomicInteger estado = new AtomicInteger(-1);

    private volatile ResultadoDoCanario ultimo;
    private volatile Instant verificadoEm;

    public CanarioScheduler(CanarioPort canario, MeterRegistry registro) {
        this.canario = canario;

        Gauge.builder("flightmonitor.canario.saudavel", estado, AtomicInteger::get)
                .description("Fontes externas respondendo no formato esperado: 1 sim, 0 nao, -1 nao consultado")
                .register(registro);
    }

    @Scheduled(
            initialDelayString = "${flightmonitor.canario.initial-delay:PT2M}",
            fixedDelayString = "${flightmonitor.canario.interval:PT24H}")
    public void verificar() {
        ResultadoDoCanario resultado = canario.consultar();
        ultimo = resultado;
        verificadoEm = Instant.now();

        if (!resultado.consultou()) {
            estado.set(-1);
            log.warn("canario nao pode ser consultado: {}", resultado.indisponivel());
            return;
        }

        if (resultado.saudavel()) {
            estado.set(1);
            log.info("canario ok: as {} camada(s) responderam no formato esperado",
                    resultado.camadas().size());
            return;
        }

        estado.set(0);
        // ERROR, e nao WARN: formato mudou significa que o sistema vai parar de
        // alertar. E a falha mais grave que este projeto tem, e a mais silenciosa.
        for (ResultadoDoCanario.Camada camada : resultado.comProblema()) {
            log.error("canario: camada {} ({}) fora do formato — erro={} achados={}",
                    camada.numero(), camada.provider(), camada.erro(), camada.achados());
        }
    }

    /** Para o painel e o indicador de saude. Nulo antes da primeira execucao. */
    public ResultadoDoCanario ultimoResultado() {
        return ultimo;
    }

    public Instant verificadoEm() {
        return verificadoEm;
    }
}
