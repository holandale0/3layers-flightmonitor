package com.flightmonitor.core.stats.control;

/**
 * Quais observacoes entram no calculo das estatisticas.
 *
 * <h2>Por que isto e uma escolha, e nao um detalhe</h2>
 *
 * As duas camadas de coleta produzem numeros que <b>nao sao comparaveis entre
 * si</b>. Medimos divergencias de 61%, 69% e 81% entre o preco cacheado da
 * camada 1 e o preco real da camada 2 — e sempre na mesma direcao: o cache
 * subestima.
 *
 * <p>Misturar os dois cria um "normal" que nao existe em lugar nenhum. Pior:
 * como o cache puxa a media para baixo, um preco real legitimo passa a parecer
 * <i>acima</i> da media — e a deteccao de anomalia da E2.2, alimentada por isso,
 * ficaria calada exatamente quando deveria falar.
 *
 * <p>Por isso a fonte e explicita na resposta, e nao um padrao escondido. Quem
 * compara um preco confirmado tem que compara-lo com {@link #CONFIRMADAS}.
 */
public enum FonteDeStats {

    /**
     * Tudo que foi observado.
     *
     * <p>Muitas amostras, e enviesado para baixo pelo cache. Serve para
     * responder "como esta a rota", nao para julgar um preco especifico.
     */
    TODAS,

    /**
     * So o que a camada 2 verificou ao vivo.
     *
     * <p>Poucas amostras — uma por varredura, no maximo — e sao precos que
     * existiram de verdade. E a base correta para julgar uma oferta.
     */
    CONFIRMADAS
}
