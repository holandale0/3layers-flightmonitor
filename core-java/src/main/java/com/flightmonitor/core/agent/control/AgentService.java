package com.flightmonitor.core.agent.control;

import com.flightmonitor.core.agent.control.MonitorIntent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.flightmonitor.core.common.ConflitoException;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.monitor.control.MonitorService;
import com.flightmonitor.core.monitor.control.dto.MonitorRequest;
import com.flightmonitor.core.monitor.control.dto.MonitorResponse;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;

/**
 * Transforma um pedido escrito em monitor de verdade — etapa E3.2.
 *
 * <h2>Tres coisas que este servico se recusa a fazer</h2>
 *
 * <ol>
 *   <li><b>Criar a partir de pedido incompleto.</b> Falta preco, ou destino, ou
 *       periodo? Nao cria, e diz o que falta. Um monitor com campo chutado nao
 *       da erro — ele vigia a coisa errada por meses, em silencio;</li>
 *   <li><b>Assumir em silencio.</b> Todo valor que o texto nao disse e que o
 *       monitor precisa ter aparece em {@code assumido}, com o valor escolhido.
 *       Padrao invisivel e a forma mais educada de mentir;</li>
 *   <li><b>Duplicar.</b> Reenviar a mesma frase e o acidente mais provavel de um
 *       endpoint conversacional, e dois monitores iguais dobram as buscas e os
 *       alertas.</li>
 * </ol>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** Intervalo padrao entre varreduras, quando o texto nao diz nada. */
    private static final int INTERVALO_PADRAO_MINUTOS = 360;

    private final NlpPort nlp;
    private final MonitorService monitores;
    private final MonitorRepository repositorio;
    private final RecipientRepository destinatarios;

    public AgentService(
            NlpPort nlp,
            MonitorService monitores,
            MonitorRepository repositorio,
            RecipientRepository destinatarios) {
        this.nlp = nlp;
        this.monitores = monitores;
        this.repositorio = repositorio;
        this.destinatarios = destinatarios;
    }

    public MonitorIntent interpretar(String texto, String origemPadrao) {
        // A data vai daqui, e nao do relogio do worker: os dois processos podem
        // estar em containers ou fusos diferentes (D-079).
        return nlp.interpretar(texto, origemPadrao, LocalDate.now());
    }

    /**
     * Interpreta e cria, ou explica por que nao deu.
     *
     * @param recipientIds quem recebe os alertas. Vazio deixa o servico
     *        resolver, e o que ele decidir aparece em {@code assumido}
     */
    public ResultadoDaCriacao criar(String texto, String origemPadrao, Set<Long> recipientIds) {
        MonitorIntent intent = interpretar(texto, origemPadrao);

        List<String> faltando = intent.faltando();
        if (!faltando.isEmpty()) {
            log.info("pedido nao virou monitor, faltou: {}", faltando);
            return ResultadoDaCriacao.recusado(intent, faltando);
        }

        Monitor equivalente = procurarEquivalente(intent);
        if (equivalente != null) {
            // 409, e nao criar em silencio: dois monitores iguais dobram as
            // buscas nas fontes e os alertas no celular de quem recebe.
            throw new ConflitoException(
                    "ja existe um monitor ativo para %s-%s nesse periodo (id %d)"
                            .formatted(intent.origin(), intent.destination(), equivalente.getId()));
        }

        List<String> assumido = new ArrayList<>();
        Set<Long> destino = resolverDestinatarios(recipientIds, assumido);

        MonitorResponse criado = monitores.criar(montarPedido(intent, destino, assumido));

        log.info("monitor {} criado por conversa: {}->{} ate {}",
                criado.id(), criado.origin(), criado.destination(), criado.maxPrice());

        return ResultadoDaCriacao.criado(criado, intent, assumido, avisos(intent, destino));
    }

    // ------------------------------------------------------------ duplicata

    /**
     * Monitor ativo, mesma rota, janela de partida que se sobrepoe.
     *
     * <p>Sobreposicao e nao igualdade: "Lisboa em marco" e "Lisboa entre 10 e
     * 20 de marco" sao o mesmo pedido dito de dois jeitos, e criar os dois
     * geraria alertas em dobro para a mesma viagem.
     */
    private Monitor procurarEquivalente(MonitorIntent intent) {
        return repositorio.findByOriginAndDestination(intent.origin(), intent.destination())
                .stream()
                .filter(Monitor::isActive)
                .filter(m -> !m.getDepartureWindowEnd().isBefore(intent.departureFrom())
                        && !m.getDepartureWindowStart().isAfter(intent.departureTo()))
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------- destinatarios

    /**
     * Quem recebe, quando o pedido nao diz.
     *
     * <p>Com exatamente um destinatario ativo cadastrado, ele e usado — e isso
     * aparece em {@code assumido}. Nao e adivinhacao: o sistema e de uso pessoal
     * por desenho (D-005), e com um destinatario so nao existe outra escolha
     * possivel.
     *
     * <p>Com varios, nao ha como escolher, e o monitor nasce sem destinatario —
     * com aviso, porque monitor sem destinatario <b>nunca alerta</b>.
     */
    private Set<Long> resolverDestinatarios(Set<Long> informados, List<String> assumido) {
        if (informados != null && !informados.isEmpty()) {
            return informados;
        }

        List<Recipient> ativos = destinatarios.findAll().stream()
                .filter(Recipient::isActive)
                .toList();

        if (ativos.size() == 1) {
            Recipient unico = ativos.get(0);
            assumido.add("destinatario: %s (o unico ativo cadastrado)".formatted(unico.getName()));
            return Set.of(unico.getId());
        }

        return Set.of();
    }

    private List<String> avisos(MonitorIntent intent, Set<Long> destinatariosDoMonitor) {
        List<String> avisos = new ArrayList<>(intent.avisos());

        if (destinatariosDoMonitor.isEmpty()) {
            // O monitor vai funcionar, varrer e registrar historico — e nunca
            // avisar ninguem. Sem esta frase, o silencio pareceria "nao achei
            // nada barato".
            avisos.add("este monitor nao tem destinatario: ele vai buscar precos, mas nao vai avisar ninguem");
        }
        return avisos;
    }

    // -------------------------------------------------------------- mapeamento

    private MonitorRequest montarPedido(
            MonitorIntent intent, Set<Long> destinatariosDoMonitor, List<String> assumido) {

        short passageiros = intent.passengers() == null ? 1 : intent.passengers();
        if (intent.passengers() == null) {
            assumido.add("1 passageiro");
        }
        assumido.add("varredura a cada %d horas".formatted(INTERVALO_PADRAO_MINUTOS / 60));

        if (intent.minStayDays() == null && intent.maxStayDays() == null) {
            assumido.add("sem restricao de permanencia");
        }
        if (intent.maxStops() == null) {
            assumido.add(intent.prefereVooDireto()
                    ? "escalas permitidas, mas penalizadas na nota"
                    : "sem limite de escalas");
        }

        return new MonitorRequest(
                intent.label(),
                intent.origin(),
                intent.destination(),
                intent.departureFrom(),
                intent.departureTo(),
                null,
                null,
                intent.minStayDays(),
                intent.maxStayDays(),
                intent.maxPrice(),
                intent.currency(),
                intent.maxStops(),
                passageiros,
                Boolean.TRUE,
                INTERVALO_PADRAO_MINUTOS,
                destinatariosDoMonitor,
                intent.prefereVooDireto(),
                Set.copyOf(intent.avoidedAirlines()),
                null,
                null,
                null,
                null);
    }

    /**
     * @param criado o monitor, ou {@code null} quando o pedido nao deu
     * @param assumido tudo que o sistema escolheu por conta propria
     */
    public record ResultadoDaCriacao(
            boolean sucesso,
            MonitorResponse criado,
            MonitorIntent intencao,
            List<String> faltando,
            List<String> assumido,
            List<String> avisos,
            String mensagem) {

        static ResultadoDaCriacao criado(
                MonitorResponse monitor, MonitorIntent intent,
                List<String> assumido, List<String> avisos) {

            return new ResultadoDaCriacao(
                    true, monitor, intent, List.of(), assumido, avisos,
                    "monitor criado; confira os campos e ajuste se precisar");
        }

        static ResultadoDaCriacao recusado(MonitorIntent intent, List<String> faltando) {
            return new ResultadoDaCriacao(
                    false, null, intent, faltando, List.of(), intent.avisos(),
                    "nao criei o monitor porque faltou: " + String.join(", ", faltando));
        }
    }
}
