package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.RouteStatsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.stats.entity.RouteStatsRepository.ResumoDaRota;
import com.flightmonitor.core.stats.entity.RouteStatsRepository.ResumoMensal;

/**
 * Estatisticas de preco por rota — etapa E2.1.
 *
 * <h2>Por que isto vive no Java, e nao no worker</h2>
 *
 * O roteiro dizia "estatisticas de rota <b>no worker</b>". Ficou no Java, e a
 * mudanca e deliberada — ver D-059. Em resumo: o historico e do banco, o banco e
 * do Java (regra 1), e mediana, desvio e quartis sao exatamente o que o
 * PostgreSQL faz melhor. Levar milhares de linhas ate o Python para calcular uma
 * mediana seria mais lento, mais fragil e violaria a regra 1 ou exigiria um
 * endpoint de despejo de dados. A regra 2 reforca: o worker "nao decide se um
 * preco e bom", e definir o que e normal e o primeiro passo dessa decisao.
 *
 * <p>O Python continua sendo o especialista em <b>coleta</b>. Se a Fase 2
 * chegar a algo que o SQL nao faca — regressao, sazonalidade, previsao —, aquilo
 * sim vai para o worker.
 */
@Service
public class RouteStatsService {

    private final RouteStatsRepository repositorio;
    private final StatsProperties props;

    public RouteStatsService(RouteStatsRepository repositorio, StatsProperties props) {
        this.repositorio = repositorio;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public RouteStats resumir(String origin, String destination, FonteDeStats fonte) {
        return resumir(origin, destination, fonte, props.janela());
    }

    @Transactional(readOnly = true)
    public RouteStats resumir(
            String origin, String destination, FonteDeStats fonte, Duration janela) {

        String o = normalizar(origin);
        String d = normalizar(destination);
        FonteDeStats f = fonte == null ? FonteDeStats.TODAS : fonte;

        ResumoDaRota bruto = repositorio.resumir(
                o, d, desde(janela), f == FonteDeStats.CONFIRMADAS);

        // count(*) sempre volta, mesmo sem linhas: a linha existe, com zero.
        if (bruto == null || bruto.getAmostras() == 0) {
            return RouteStats.vazio(o, d, f);
        }

        int amostras = (int) bruto.getAmostras();

        return new RouteStats(
                o, d, f,
                amostras,
                amostras >= props.minimoAmostras(),
                dinheiro(bruto.getMinimo()),
                dinheiro(bruto.getP25()),
                dinheiro(bruto.getMediana()),
                dinheiro(bruto.getMedia()),
                dinheiro(bruto.getP75()),
                dinheiro(bruto.getMaximo()),
                // Nulo com uma amostra so, e assim permanece: devolver zero
                // afirmaria "rota estavel", que e uma conclusao, nao um dado.
                dinheiro(bruto.getDesvio()),
                bruto.getPrimeira(),
                bruto.getUltima());
    }

    @Transactional(readOnly = true)
    public List<MesDaRota> resumirPorMes(String origin, String destination, FonteDeStats fonte) {
        return resumirPorMes(origin, destination, fonte, props.janela());
    }

    @Transactional(readOnly = true)
    public List<MesDaRota> resumirPorMes(
            String origin, String destination, FonteDeStats fonte, Duration janela) {

        String o = normalizar(origin);
        String d = normalizar(destination);
        FonteDeStats f = fonte == null ? FonteDeStats.TODAS : fonte;

        List<ResumoMensal> linhas = repositorio.resumirPorMes(
                o, d, desde(janela), f == FonteDeStats.CONFIRMADAS);

        return linhas.stream()
                .map(l -> new MesDaRota(
                        l.getMes(),
                        (int) l.getAmostras(),
                        (int) l.getAmostras() >= props.minimoAmostras(),
                        dinheiro(l.getMinimo()),
                        dinheiro(l.getMediana()),
                        dinheiro(l.getMedia()),
                        dinheiro(l.getMaximo())))
                .toList();
    }

    private Instant desde(Duration janela) {
        Duration efetiva = janela == null ? props.janela() : janela;
        return Instant.now().minus(efetiva);
    }

    private String normalizar(String iata) {
        return iata == null ? null : iata.trim().toUpperCase();
    }

    /**
     * Arredonda para centavos.
     *
     * <p>Mediana e quartis saem do PostgreSQL como {@code double precision} —
     * {@code percentile_cont} nao preserva {@code numeric}. Devolver
     * {@code 3479.9999999999995} numa API de precos e ruido; duas casas e o que
     * dinheiro tem.
     */
    private BigDecimal dinheiro(Number valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal b = valor instanceof BigDecimal bd
                ? bd
                : BigDecimal.valueOf(valor.doubleValue());
        return b.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Um mes de partida da rota.
     *
     * @param mes formato {@code YYYY-MM}, referente a data de <b>partida</b>
     * @param confiavel se o mes tem amostras suficientes para ser comparado com
     *        os outros. Um mes com duas observacoes aparece na lista — a
     *        ausencia de dado tambem e informacao —, mas nao deve ser
     *        apresentado como "o mais barato"
     */
    public record MesDaRota(
            String mes,
            int amostras,
            boolean confiavel,
            BigDecimal minimo,
            BigDecimal mediana,
            BigDecimal media,
            BigDecimal maximo) {
    }
}
