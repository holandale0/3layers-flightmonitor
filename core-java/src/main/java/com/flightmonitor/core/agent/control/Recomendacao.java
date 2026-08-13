package com.flightmonitor.core.agent.control;

import java.util.List;

/**
 * Por que esta oferta vale — ou nao vale — a pena, em portugues — etapa E3.3.
 *
 * <p>A Fase 2 produziu quatro analises que ninguem le: estatistica da rota,
 * anomalia de preco, nota do voo e tendencia. Esta e a peca que junta as quatro
 * numa frase que uma pessoa entende.
 *
 * <h2>O texto vem com as razoes, e nao no lugar delas</h2>
 *
 * O {@code resumo} e para ler; as {@link Razao} sao para conferir. Uma
 * recomendacao sem a lista seria opiniao sem prestacao de contas — e a primeira
 * vez que ela errasse, nao haveria como descobrir de onde saiu.
 *
 * @param confiavel se as analises por tras se sustentam. Falso quando a rota
 *        ainda tem pouco historico — e ai o veredito e {@link Veredito#SEM_BASE}
 */
public record Recomendacao(
        Veredito veredito,
        List<Razao> razoes,
        String resumo,
        boolean confiavel) {

    public Recomendacao {
        razoes = razoes == null ? List.of() : List.copyOf(razoes);
    }

    /**
     * O quanto a oferta se recomenda.
     *
     * <p><b>Nao existe "compre agora".</b> A D-072 vale aqui tambem: o sistema
     * informa, e quem viaja decide. A diferenca entre "vale muito a pena" e
     * "compre" parece pequena e nao e — a segunda assume um risco que nao e do
     * sistema, e a primeira vez que ela errasse feio custaria a confianca em
     * tudo o mais.
     */
    public enum Veredito {

        /**
         * Nao ha historico suficiente para opinar.
         *
         * <p>Distinto de {@link #NAO_RECOMENDO}: la olhamos e nao gostamos, aqui
         * nao temos com que comparar.
         */
        SEM_BASE,

        /** Preco alto para a rota, ou voo ruim, ou os dois. */
        NAO_RECOMENDO,

        /** Nada de especial em nenhuma direcao. */
        TALVEZ,

        /** Bom preco, ou bom voo. */
        VALE,

        /** Preco atipico e voo bom: a combinacao que o sistema existe para achar. */
        VALE_MUITO;

        /** Merece virar mensagem, ou e ruido? */
        public boolean vaiNaMensagem() {
            return this == VALE || this == VALE_MUITO;
        }
    }

    /**
     * Um argumento, com o lado dele.
     *
     * @param frase escrita para gente, sem jargao de estatistica
     */
    public record Razao(Aspecto aspecto, Peso peso, String frase) {

        public enum Aspecto { PRECO, VOO, TENDENCIA, HISTORICO }

        public enum Peso {
            A_FAVOR,
            CONTRA,
            /**
             * Nem a favor nem contra: e informacao que muda a decisao sem
             * apontar direcao.
             *
             * <p>A tendencia de queda e o caso tipico. Ela nao torna a oferta
             * pior — torna razoavel esperar. Transformar isso em "contra" seria
             * dar conselho disfarcado de fato.
             */
            A_PONDERAR
        }
    }
}
