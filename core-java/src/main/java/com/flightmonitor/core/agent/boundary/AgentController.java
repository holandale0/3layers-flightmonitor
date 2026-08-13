package com.flightmonitor.core.agent.boundary;

import com.flightmonitor.core.agent.control.MonitorIntent;

import com.flightmonitor.core.agent.control.RecomendacaoService;
import com.flightmonitor.core.agent.control.Recomendacao;
import com.flightmonitor.core.agent.control.AgentService;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.PageRequest;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O agente conversacional — etapa E3.1.
 *
 * <p>Aqui ele apenas <b>interpreta</b>. Criar o monitor e da E3.2, e a
 * separacao e proposital: quem le um pedido deve poder conferir o que o sistema
 * entendeu <b>antes</b> de qualquer coisa ser gravada.
 *
 * <p>Um monitor criado a partir de interpretacao errada nao da erro — ele
 * simplesmente vigia a rota errada por meses, em silencio. Mostrar a leitura
 * primeiro custa um clique e elimina essa classe inteira de problema.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService service;
    private final RecomendacaoService recomendacoes;
    private final PriceObservationRepository observacoes;
    private final MonitorRepository monitores;

    public AgentController(
            AgentService service,
            RecomendacaoService recomendacoes,
            PriceObservationRepository observacoes,
            MonitorRepository monitores) {
        this.service = service;
        this.recomendacoes = recomendacoes;
        this.observacoes = observacoes;
        this.monitores = monitores;
    }

    @PostMapping("/interpret")
    public InterpretacaoResponse interpretar(@Valid @RequestBody InterpretacaoRequest req) {
        MonitorIntent intent = service.interpretar(req.texto(), req.origemPadrao());

        return new InterpretacaoResponse(
                intent, intent.completo(), intent.faltando(), sugestao(intent));
    }

    /**
     * Cria o monitor a partir da frase — etapa E3.2.
     *
     * <p><b>422 quando o pedido esta incompleto</b>, e nao 400: a sintaxe do
     * pedido esta certa, o conteudo e que nao basta. A distincao importa para o
     * painel, que trata os dois de formas diferentes — um e erro de programacao,
     * o outro e conversa que continua.
     *
     * <p>O corpo do 422 traz a interpretacao e a lista do que falta, entao a
     * interface pode perguntar exatamente o que precisa em vez de mandar a
     * pessoa reescrever tudo.
     */
    @PostMapping("/monitors")
    public ResponseEntity<AgentService.ResultadoDaCriacao> criar(
            @Valid @RequestBody CriacaoRequest req) {

        AgentService.ResultadoDaCriacao resultado =
                service.criar(req.texto(), req.origemPadrao(), req.recipientIds());

        return resultado.sucesso()
                ? ResponseEntity.status(HttpStatus.CREATED).body(resultado)
                : ResponseEntity.unprocessableEntity().body(resultado);
    }

    /**
     * Por que esta oferta vale — ou nao vale — a pena, em portugues — E3.3.
     *
     * <p>Sobre uma observacao ja gravada, e nao sobre um voo hipotetico: a
     * recomendacao nasce de comparacao com o historico, entao so faz sentido
     * para algo que o sistema realmente viu.
     */
    @GetMapping("/recommendation/observations/{id}")
    public Recomendacao recomendarObservacao(@PathVariable Long id) {
        PriceObservation o = observacoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Observacao", id));

        // O monitor vem junto porque os pesos do score sao dele (E2.6). Pode ser
        // nulo: a FK e ON DELETE SET NULL para o historico sobreviver (D-016).
        Monitor m = o.getMonitor() == null
                ? null
                : monitores.findByIdComPreferencias(o.getMonitor().getId()).orElse(null);

        return recomendacoes.recomendar(o, m);
    }

    /**
     * A melhor oferta confirmada deste monitor, explicada.
     *
     * <p>E a pergunta que o painel realmente faz — "vale a pena o que voce achou
     * pra mim?" —, sem obrigar quem consome a descobrir antes qual observacao
     * interessa.
     */
    @GetMapping("/recommendation/monitors/{id}")
    public ResponseEntity<Recomendacao> recomendarMonitor(@PathVariable Long id) {
        Monitor m = monitores.findByIdComPreferencias(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));

        return observacoes.melhorConfirmadaDoMonitor(id, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(o -> ResponseEntity.ok(recomendacoes.recomendar(o, m)))
                // 204 e nao 404: o monitor existe, so ainda nao encontrou nada
                // confirmado. Um 404 diria que o monitor nao existe.
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * O que dizer a quem escreveu o pedido.
     *
     * <p>Uma frase, em portugues, dizendo exatamente o que falta. "Interpretacao
     * incompleta" nao ajuda ninguem; "faltou dizer o preco maximo" resolve o
     * problema na hora.
     */
    private String sugestao(MonitorIntent intent) {
        List<String> faltando = intent.faltando();

        if (faltando.isEmpty()) {
            return "entendi o pedido; confira os campos antes de criar o monitor";
        }
        if (faltando.size() == 1) {
            return "faltou dizer: " + faltando.get(0);
        }
        return "faltou dizer: " + String.join(", ", faltando);
    }

    /**
     * @param recipientIds quem recebe os alertas. Vazio deixa o servico
     *        resolver, e a resposta diz o que ele decidiu
     */
    public record CriacaoRequest(
            @Size(min = 3, max = 1000, message = "escreva entre 3 e 1000 caracteres")
            String texto,

            @Pattern(regexp = "^[A-Za-z]{3}$", message = "deve ser um codigo IATA de 3 letras")
            String origemPadrao,

            Set<Long> recipientIds) {
    }

    /**
     * @param origemPadrao de onde a pessoa costuma sair. Sem isto, "quero ir pra
     *        Lisboa" nao tem origem — e chutar a origem seria pior do que
     *        perguntar
     */
    public record InterpretacaoRequest(
            @Size(min = 3, max = 1000, message = "escreva entre 3 e 1000 caracteres")
            String texto,

            @Pattern(regexp = "^[A-Za-z]{3}$", message = "deve ser um codigo IATA de 3 letras")
            String origemPadrao) {
    }

    /**
     * @param completo se ja da para criar o monitor sem perguntar mais nada
     * @param faltando o que impede, em portugues
     */
    public record InterpretacaoResponse(
            MonitorIntent intencao,
            boolean completo,
            List<String> faltando,
            String sugestao) {
    }
}
