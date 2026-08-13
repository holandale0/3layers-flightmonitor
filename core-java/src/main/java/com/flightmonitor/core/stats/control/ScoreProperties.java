package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Pesos e limiares do Flight Score — etapa E2.3.
 *
 * <h2>Por que os pesos sao configuracao, e nao constantes no codigo</h2>
 *
 * Nao existe peso certo. Quanto uma escala vale em relacao a duzentos reais e
 * uma preferencia pessoal, e quem escreve o codigo nao tem autoridade sobre a
 * viagem de quem usa. Deixa-los aqui torna a opiniao <b>visivel e ajustavel</b>,
 * em vez de escondida em quatro numeros mágicos espalhados por um metodo.
 *
 * <p>Na E2.6 eles passam a poder ser sobrescritos por monitor — quem vai a
 * trabalho e quem vai passear nao querem a mesma coisa.
 *
 * @param pesoPreco peso do preco. O maior de todos de proposito: o sistema
 *        inteiro existe para achar passagem barata, e uma nota que premiasse
 *        conforto acima de preco contradiria o proprio produto
 * @param coberturaMinima fracao do peso que precisa estar disponivel para a nota
 *        ser considerada confiavel. Abaixo disso a nota ainda sai — e informacao
 *        —, marcada como nao confiavel
 */
@ConfigurationProperties(prefix = "flightmonitor.score")
public record ScoreProperties(
        int pesoPreco,
        int pesoEscalas,
        int pesoDuracao,
        int pesoHorario,
        BigDecimal coberturaMinima,
        /**
         * Vem de escolha explicita do monitor? Nesse caso zero e respeitado.
         *
         * <p>No caminho da configuracao, zero significa "esqueci de preencher" e
         * vira o padrao. Vindo do monitor, significa "nao me importo com isso",
         * e trocar por 50 seria desfazer a escolha da pessoa.
         */
        boolean doMonitor) {

    /**
     * Construtor da configuracao: pesos ausentes viram os padroes.
     *
     * <p>{@code @ConstructorBinding} e obrigatorio aqui. Com dois construtores,
     * o Spring nao tem como adivinhar qual usar para ligar as propriedades — e
     * falha na subida com "No default constructor found", que aponta para o
     * lugar errado. A anotacao diz qual e o de configuracao; o outro existe so
     * para as preferencias do monitor.
     */
    @ConstructorBinding
    public ScoreProperties(
            int pesoPreco, int pesoEscalas, int pesoDuracao, int pesoHorario,
            BigDecimal coberturaMinima) {
        this(pesoPreco, pesoEscalas, pesoDuracao, pesoHorario, coberturaMinima, false);
    }

    public ScoreProperties {
        if (!doMonitor) {
            pesoPreco = positivoOu(pesoPreco, 50);
            pesoEscalas = positivoOu(pesoEscalas, 20);
            pesoDuracao = positivoOu(pesoDuracao, 20);
            pesoHorario = positivoOu(pesoHorario, 10);
        } else {
            // Escolha do monitor: zero vale. Negativo nao — inverteria o sentido
            // da nota, e nao existe "quero voo com mais escalas".
            pesoPreco = Math.max(0, pesoPreco);
            pesoEscalas = Math.max(0, pesoEscalas);
            pesoDuracao = Math.max(0, pesoDuracao);
            pesoHorario = Math.max(0, pesoHorario);
        }

        if (coberturaMinima == null
                || coberturaMinima.signum() <= 0
                || coberturaMinima.compareTo(BigDecimal.ONE) > 0) {
            // 0,5 e o suficiente para preco + escalas, que e o que uma
            // observacao da camada 1 consegue oferecer.
            coberturaMinima = new BigDecimal("0.5");
        }
    }

    public int pesoTotal() {
        return pesoPreco + pesoEscalas + pesoDuracao + pesoHorario;
    }

    /**
     * Aplica os pesos que o monitor tiver escolhido, mantendo os globais no
     * resto — etapa E2.6.
     *
     * <p>Sobrescrita campo a campo, e nao tudo ou nada: quem so quer dizer
     * "escala me incomoda muito" nao deveria ser obrigado a reinventar os outros
     * tres pesos, e provavelmente escolheria pior do que o padrao.
     *
     * <p>Peso <b>zero e valido</b> e significa "este aspecto nao me importa" —
     * diferente de nulo, que e "nao escolhi". O construtor canonico troca zero
     * pelo padrao, entao a substituicao acontece aqui, direto no record.
     */
    public ScoreProperties com(Short preco, Short escalas, Short duracao, Short horario) {
        if (preco == null && escalas == null && duracao == null && horario == null) {
            return this;
        }
        return new ScoreProperties(
                preco == null ? pesoPreco : preco,
                escalas == null ? pesoEscalas : escalas,
                duracao == null ? pesoDuracao : duracao,
                horario == null ? pesoHorario : horario,
                coberturaMinima,
                true);
    }

    private static int positivoOu(int valor, int padrao) {
        return valor > 0 ? valor : padrao;
    }
}
