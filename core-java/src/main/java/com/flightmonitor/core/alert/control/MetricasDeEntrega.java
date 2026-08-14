package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertChannel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * Metricas da entrega de alertas — etapa E4.3.
 *
 * <h2>O que se mede, e por que separado da busca</h2>
 *
 * Encontrar a passagem e avisar alguem sao dois sistemas que falham por motivos
 * diferentes. A varredura quebra quando a <b>fonte</b> muda de formato; a
 * entrega quebra quando o <b>template</b> e recusado, o token expira ou a senha
 * de app e revogada.
 *
 * <p>Misturar os dois numa metrica so produziria "o sistema esta com problema"
 * sem dizer em qual metade — e as duas metades tem donos e prazos de conserto
 * bem diferentes.
 *
 * <h2>Por que separar transitoria de permanente</h2>
 *
 * A distincao ja existe no {@link DeliveryResult} porque muda o comportamento —
 * transitoria retenta, permanente nao. Aqui ela muda o <b>alarme</b>: falha
 * transitoria subindo e a Meta instavel, e passa; falha permanente subindo e
 * configuracao errada, e nao passa sozinha. A primeira da para dormir; a
 * segunda, nao.
 */
@Component
public class MetricasDeEntrega {

    private final MeterRegistry registro;

    public MetricasDeEntrega(MeterRegistry registro) {
        this.registro = registro;
    }

    public void registrar(AlertChannel canal, DeliveryResult resultado) {
        Counter.builder("flightmonitor.entrega.tentativas")
                .description("Tentativas de entrega de alerta, por canal e resultado")
                .tag("canal", canal.name().toLowerCase())
                .tag("resultado", classificar(resultado))
                .register(registro)
                .increment();
    }

    /**
     * Confirmacao vinda de fora — o webhook da Meta.
     *
     * <p>Separada da tentativa de proposito: {@code entregue} aqui significa que
     * a mensagem <b>chegou no aparelho</b>, e nao que a Meta aceitou. Foi a
     * licao do BUG-007, e ela vale para a metrica tanto quanto para o estado do
     * alerta: {@code entrega.tentativas} alto com {@code confirmacoes} zerado e
     * exatamente o sintoma daquele bug — aceito por todos, entregue a ninguem.
     */
    public void registrarConfirmacao(String tipo) {
        Counter.builder("flightmonitor.entrega.confirmacoes")
                .description("Confirmacoes recebidas do provedor (delivered, read, failed)")
                .tag("tipo", tipo.toLowerCase())
                .register(registro)
                .increment();
    }

    private static String classificar(DeliveryResult resultado) {
        if (resultado.sucesso()) {
            return "aceita";
        }
        return resultado.transitorio() ? "falha_transitoria" : "falha_permanente";
    }
}
