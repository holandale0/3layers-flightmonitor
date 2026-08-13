package com.flightmonitor.core.search.control;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo de uma varredura, entregue a quem decide o que fazer com ela.
 *
 * <p>A etapa E1.10 vai consumir isto para aplicar a regra de alerta. Por isso o
 * resumo distingue os desfechos em vez de simplificar: <b>ter uma oferta boa</b>,
 * <b>ter uma oferta boa mas nao confirmada</b> e <b>nao ter oferta</b> levam a
 * decisoes diferentes.
 *
 * @param melhorObservacaoId a melhor observacao abaixo do teto, ou {@code null}
 * @param confirmada         se a melhor observacao passou pela camada 2
 * @param camada2Degradada   nenhuma fonte de confirmacao respondeu; nao sabemos
 * @param candidatoIlusorio  o candidato da camada 1 <b>nao se sustentou</b> ao ser
 *        confirmado. Cobre dois casos que, para a decisao de alertar, dao no
 *        mesmo: o voo nao existe, ou existe mas o preco real estourou o teto.
 *        Medido em execucao real: cache anunciava R$ 3.375 e o preco real era
 *        R$ 5.714, acima do teto de R$ 4.000. Os avisos dizem qual dos dois foi
 */
public record SearchOutcome(
        Long monitorId,
        int observacoesGravadas,
        int candidatosAbaixoDoTeto,
        Long melhorObservacaoId,
        BigDecimal melhorPreco,
        boolean confirmada,
        boolean camada2Degradada,
        boolean candidatoIlusorio,
        boolean falhou,
        List<String> avisos) {

    public SearchOutcome {
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    public static SearchOutcome semOportunidade(
            Long monitorId, int gravadas, List<String> avisos) {
        return new SearchOutcome(
                monitorId, gravadas, 0, null, null, false, false, false, false, avisos);
    }

    public static SearchOutcome falha(Long monitorId, String motivo) {
        return new SearchOutcome(
                monitorId, 0, 0, null, null, false, false, false, true, List.of(motivo));
    }

    /** Ha uma oferta que vale a pena avaliar para alerta (etapa E1.10). */
    public boolean temOportunidade() {
        return melhorObservacaoId != null && !candidatoIlusorio;
    }
}
