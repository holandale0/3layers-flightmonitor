package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.RouteStatsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.stats.control.FlightScore.ComponenteDoScore;

/**
 * Da nota a um voo — etapa E2.3.
 *
 * <h2>A regra que governa tudo aqui: nulo nao e zero</h2>
 *
 * Uma observacao da camada 1 nao tem duracao nem horario. A saida preguicosa
 * seria pontuar zero nesses aspectos, e ela e <b>ativamente nociva</b>: faria
 * toda oferta nao confirmada parecer ruim, e o sistema passaria a preferir voos
 * so porque tinham mais dados — nao porque eram melhores.
 *
 * <p>Aqui o aspecto sem dado sai com nota nula, o peso dele sai da conta, e a
 * nota final e renormalizada sobre o que sobrou. A {@code cobertura} registra
 * quanto do peso foi de fato avaliado, para que ninguem confunda uma nota de
 * quatro aspectos com uma de dois.
 *
 * <h2>Toda referencia e da propria rota</h2>
 *
 * Nem preco nem duracao tem limite absoluto que faca sentido: R$ 3.000 e caro
 * para Sao Paulo-Rio e barato para Sao Paulo-Toquio; dez horas e otimo numa e
 * absurdo na outra. As duas notas nascem de comparacao com o historico da mesma
 * rota.
 */
@Service
public class FlightScoreService {

    private final RouteStatsService estatisticas;
    private final RouteStatsRepository repositorio;
    private final StatsProperties statsProps;
    private final ScoreProperties props;

    public FlightScoreService(
            RouteStatsService estatisticas,
            RouteStatsRepository repositorio,
            StatsProperties statsProps,
            ScoreProperties props) {
        this.estatisticas = estatisticas;
        this.repositorio = repositorio;
        this.statsProps = statsProps;
        this.props = props;
    }

    /** Busca a referencia da rota e pontua, com os pesos globais. */
    @Transactional(readOnly = true)
    public FlightScore pontuar(PriceObservation oferta, FonteDeStats fonte) {
        return pontuar(oferta, fonte, null);
    }

    /**
     * Pontua respeitando as preferencias do monitor — etapa E2.6.
     *
     * @param monitor de onde saem os pesos e a preferencia por voo direto.
     *        {@code null} usa os pesos globais
     */
    @Transactional(readOnly = true)
    public FlightScore pontuar(PriceObservation oferta, FonteDeStats fonte, Monitor monitor) {
        if (oferta == null) {
            return FlightScore.indisponivel("nenhuma oferta para avaliar");
        }

        RouteStats ref = estatisticas.resumir(oferta.getOrigin(), oferta.getDestination(), fonte);
        Integer duracaoMinima = repositorio.duracaoMinimaDaRota(
                oferta.getOrigin(),
                oferta.getDestination(),
                Instant.now().minus(statsProps.janela()));

        return pontuar(oferta, ref, duracaoMinima, monitor);
    }

    /** Pontua com a referencia ja em maos, com os pesos globais. */
    public FlightScore pontuar(PriceObservation oferta, RouteStats ref, Integer duracaoMinima) {
        return pontuar(oferta, ref, duracaoMinima, null);
    }

    /** Pontua com a referencia ja em maos e as preferencias do monitor. */
    public FlightScore pontuar(
            PriceObservation oferta, RouteStats ref, Integer duracaoMinima, Monitor monitor) {

        if (oferta == null) {
            return FlightScore.indisponivel("nenhuma oferta para avaliar");
        }

        ScoreProperties pesos = pesosDe(monitor);
        boolean prefereDireto = monitor != null && monitor.isPrefereVooDireto();

        List<ComponenteDoScore> componentes = List.of(
                avaliarPreco(oferta, ref, pesos),
                avaliarEscalas(oferta, pesos, prefereDireto),
                avaliarDuracao(oferta, duracaoMinima, pesos),
                avaliarHorario(oferta, pesos));

        int pesoAvaliado = componentes.stream()
                .filter(ComponenteDoScore::avaliado)
                .mapToInt(ComponenteDoScore::peso)
                .sum();

        if (pesoAvaliado == 0) {
            return new FlightScore(null, false, BigDecimal.ZERO, componentes,
                    "nenhum aspecto pode ser avaliado com os dados disponiveis");
        }

        // Media ponderada apenas sobre o que existe. Somar zeros pelos ausentes
        // faria uma oferta sem detalhe de voo parecer pior do que e.
        int soma = componentes.stream()
                .filter(ComponenteDoScore::avaliado)
                .mapToInt(c -> c.nota() * c.peso())
                .sum();

        int nota = Math.round((float) soma / pesoAvaliado);

        BigDecimal cobertura = BigDecimal.valueOf(pesoAvaliado)
                .divide(BigDecimal.valueOf(pesos.pesoTotal()), 2, RoundingMode.HALF_UP);

        boolean confiavel = ref != null
                && ref.confiavel()
                && cobertura.compareTo(pesos.coberturaMinima()) >= 0;

        return new FlightScore(nota, confiavel, cobertura, componentes,
                explicar(nota, confiavel, componentes));
    }

    // ------------------------------------------------------------- preco

    /**
     * Onde o preco cai na distribuicao da rota, em degraus lineares por quartil.
     *
     * <p>Interpolacao entre quantis, e nao entre minimo e maximo: um unico preco
     * absurdo no historico comprimiria toda a escala e faria quase tudo parecer
     * bom. Os quantis nao se movem com isso — mesma razao da escolha da regra de
     * Tukey na E2.2.
     */
    private ComponenteDoScore avaliarPreco(
            PriceObservation oferta, RouteStats ref, ScoreProperties props) {

        if (oferta.getPrice() == null) {
            return semNota(AspectoDoVoo.PRECO, props.pesoPreco(), "oferta sem preco");
        }
        if (ref == null || !ref.temDados() || ref.mediana() == null) {
            return semNota(AspectoDoVoo.PRECO, props.pesoPreco(),
                    "sem historico da rota para comparar");
        }

        BigDecimal p = oferta.getPrice();

        if (ref.minimo() != null && p.compareTo(ref.minimo()) <= 0) {
            return new ComponenteDoScore(AspectoDoVoo.PRECO, 100, props.pesoPreco(),
                    "menor preco ja visto na rota");
        }
        if (ref.p25() != null && p.compareTo(ref.p25()) <= 0) {
            return new ComponenteDoScore(AspectoDoVoo.PRECO,
                    interpolar(p, ref.minimo(), ref.p25(), 100, 80), props.pesoPreco(),
                    "entre os 25% mais baratos da rota");
        }
        if (p.compareTo(ref.mediana()) <= 0) {
            return new ComponenteDoScore(AspectoDoVoo.PRECO,
                    interpolar(p, ref.p25(), ref.mediana(), 80, 60), props.pesoPreco(),
                    "abaixo da mediana da rota");
        }
        if (ref.p75() != null && p.compareTo(ref.p75()) <= 0) {
            return new ComponenteDoScore(AspectoDoVoo.PRECO,
                    interpolar(p, ref.mediana(), ref.p75(), 60, 35), props.pesoPreco(),
                    "acima da mediana da rota");
        }
        return new ComponenteDoScore(AspectoDoVoo.PRECO,
                interpolar(p, ref.p75(), ref.maximo(), 35, 0), props.pesoPreco(),
                "entre os 25% mais caros da rota");
    }

    // ----------------------------------------------------------- escalas

    /**
     * Degraus fixos, e a queda do direto para uma escala e a maior.
     *
     * <p>E onde esta a diferenca real de experiencia: conexao perdida, bagagem
     * extraviada e espera em aeroporto acontecem na primeira escala. Da segunda
     * para a terceira o incomodo cresce menos do que da primeira para a segunda.
     */
    private ComponenteDoScore avaliarEscalas(
            PriceObservation oferta, ScoreProperties props, boolean prefereDireto) {

        Short escalas = oferta.getStops();
        if (escalas == null) {
            return semNota(AspectoDoVoo.ESCALAS, props.pesoEscalas(),
                    "a oferta nao informa escalas");
        }

        // Com preferencia por direto a curva fica bem mais dura. Continua sendo
        // preferencia, e nao exigencia: um voo com escala ainda pode vencer se
        // for muito mais barato. Quem quer exclusao usa maxStops, que e limite
        // rigido e nem chega a ser buscado.
        int nota = prefereDireto ? notaDuraDeEscalas(escalas) : notaNormalDeEscalas(escalas);
        String detalhe = escalas == 0 ? "voo direto" : escalas + " escala(s)";

        return new ComponenteDoScore(AspectoDoVoo.ESCALAS, nota, props.pesoEscalas(), detalhe);
    }

    private int notaNormalDeEscalas(short escalas) {
        return switch (Math.min(escalas, 3)) {
            case 0 -> 100;
            case 1 -> 65;
            case 2 -> 35;
            default -> 10;
        };
    }

    private int notaDuraDeEscalas(short escalas) {
        return switch (Math.min(escalas, 3)) {
            case 0 -> 100;
            case 1 -> 30;
            case 2 -> 10;
            default -> 0;
        };
    }

    // ---------------------------------------------------------- duracao

    /**
     * Duracao comparada a melhor ja vista na mesma rota.
     *
     * <p>Igualar a melhor vale 100; o dobro dela vale 0; entre os dois, linear.
     * A referencia e da rota porque limite absoluto nao existe — dez horas e
     * otimo para Sao Paulo-Lisboa e absurdo para Sao Paulo-Curitiba.
     */
    private ComponenteDoScore avaliarDuracao(
            PriceObservation oferta, Integer minimoDaRota, ScoreProperties props) {
        Integer duracao = oferta.getDurationMinutes();
        if (duracao == null || duracao <= 0) {
            return semNota(AspectoDoVoo.DURACAO, props.pesoDuracao(),
                    "a oferta nao informa duracao");
        }
        if (minimoDaRota == null || minimoDaRota <= 0) {
            // Nunca vimos duracao nesta rota: nao ha com que comparar. Chutar um
            // limite em horas seria pior do que nao avaliar.
            return semNota(AspectoDoVoo.DURACAO, props.pesoDuracao(),
                    "sem duracao de referencia para a rota");
        }

        double razao = (double) duracao / minimoDaRota;
        int nota = (int) Math.round(Math.max(0, Math.min(100, 100 - (razao - 1) * 100)));

        // Quando a oferta E a melhor da rota, comparar com ela mesma produzia
        // "12h25, contra 12h25 do melhor da rota" — verdadeiro e ridiculo.
        // Visto em execucao real, na E3.3.
        String detalhe = duracao.equals(minimoDaRota)
                ? "%dh%02d, a melhor duracao ja vista na rota".formatted(duracao / 60, duracao % 60)
                : "%dh%02d, contra %dh%02d do melhor da rota".formatted(
                        duracao / 60, duracao % 60, minimoDaRota / 60, minimoDaRota % 60);

        return new ComponenteDoScore(AspectoDoVoo.DURACAO, nota, props.pesoDuracao(), detalhe);
    }

    // ---------------------------------------------------------- horario

    /**
     * Faixas de horario de partida.
     *
     * <p>Isto e uma <b>preferencia media</b>, e nao um fato: ha quem escolha voo
     * de madrugada de proposito, por ser mais barato e mais vazio. Fica como
     * padrao razoavel ate a E2.6 permitir que o monitor diga o que prefere.
     */
    private ComponenteDoScore avaliarHorario(PriceObservation oferta, ScoreProperties props) {
        LocalDateTime partida = oferta.getDepartureAt();
        if (partida == null) {
            return semNota(AspectoDoVoo.HORARIO, props.pesoHorario(),
                    "a oferta nao informa horario de partida");
        }

        int hora = partida.getHour();
        int nota;
        String detalhe;

        if (hora >= 6 && hora < 12) {
            nota = 100;
            detalhe = "parte de manha";
        } else if (hora >= 12 && hora < 18) {
            nota = 85;
            detalhe = "parte a tarde";
        } else if (hora >= 18 && hora < 22) {
            nota = 70;
            detalhe = "parte a noite";
        } else if (hora >= 22) {
            nota = 45;
            detalhe = "parte tarde da noite";
        } else {
            nota = 25;
            detalhe = "parte de madrugada";
        }

        return new ComponenteDoScore(AspectoDoVoo.HORARIO, nota, props.pesoHorario(), detalhe);
    }

    // ------------------------------------------------------------ apoio

    /** Pesos do monitor por cima dos globais, campo a campo. */
    private ScoreProperties pesosDe(Monitor monitor) {
        if (monitor == null || !monitor.temPreferenciaDePeso()) {
            return props;
        }
        return props.com(
                monitor.getPesoPreco(),
                monitor.getPesoEscalas(),
                monitor.getPesoDuracao(),
                monitor.getPesoHorario());
    }

    private ComponenteDoScore semNota(AspectoDoVoo aspecto, int peso, String motivo) {
        return new ComponenteDoScore(aspecto, null, peso, motivo);
    }

    /**
     * Nota linear entre dois pontos de referencia.
     *
     * <p>Quanto MAIOR o valor, MENOR a nota — dai a inversao. Se os limites
     * coincidirem, devolve a nota do topo, em vez de dividir por zero.
     */
    private int interpolar(
            BigDecimal valor, BigDecimal de, BigDecimal ate, int notaEmDe, int notaEmAte) {

        if (de == null || ate == null) {
            return notaEmAte;
        }
        BigDecimal faixa = ate.subtract(de);
        if (faixa.signum() <= 0) {
            return notaEmDe;
        }

        BigDecimal posicao = valor.subtract(de)
                .divide(faixa, 4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        BigDecimal nota = BigDecimal.valueOf(notaEmDe)
                .subtract(posicao.multiply(BigDecimal.valueOf(notaEmDe - (long) notaEmAte)));

        return nota.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Frase que acompanha a nota.
     *
     * <p>Sempre nomeia o que puxou a nota para cima ou para baixo. Uma nota sem
     * isso e inauditavel: "62" nao diz se o problema foi o preco, a escala ou o
     * voo de madrugada, e sem saber disso o numero nao ajuda a decidir nada.
     */
    private String explicar(int nota, boolean confiavel, List<ComponenteDoScore> componentes) {
        ComponenteDoScore melhor = null;
        ComponenteDoScore pior = null;

        for (ComponenteDoScore c : componentes) {
            if (!c.avaliado()) {
                continue;
            }
            if (melhor == null || c.nota() > melhor.nota()) {
                melhor = c;
            }
            if (pior == null || c.nota() < pior.nota()) {
                pior = c;
            }
        }

        StringBuilder sb = new StringBuilder(faixa(nota));

        if (melhor != null) {
            sb.append("; a favor: ").append(melhor.detalhe());
        }
        // So menciona o ponto fraco quando ele realmente pesa contra. Abaixo de
        // 70 ainda ha o que dizer; acima disso, apontar defeito seria ruido.
        if (pior != null && pior != melhor && pior.nota() < 70) {
            sb.append("; contra: ").append(pior.detalhe());
        }

        long ausentes = componentes.stream().filter(c -> !c.avaliado()).count();
        if (ausentes > 0) {
            sb.append(" (avaliado sem %d aspecto(s), por falta de dado)".formatted(ausentes));
        }
        if (!confiavel) {
            sb.append(" — nota preliminar");
        }
        return sb.toString();
    }

    private String faixa(int nota) {
        if (nota >= 85) {
            return "excelente";
        }
        if (nota >= 70) {
            return "bom";
        }
        if (nota >= 50) {
            return "razoavel";
        }
        return "fraco";
    }
}
