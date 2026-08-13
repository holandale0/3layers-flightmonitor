package com.flightmonitor.core.alert.boundary.whatsapp;

import com.flightmonitor.core.alert.control.WhatsAppConfigService;
import com.flightmonitor.core.alert.control.dto.WhatsAppConfigRequest;
import com.flightmonitor.core.alert.control.dto.WhatsAppConfigResponse;
import com.flightmonitor.core.alert.entity.WhatsAppConfig;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracao do canal WhatsApp — etapa E4.7.
 *
 * <h2>O que este endpoint nunca faz</h2>
 *
 * <b>Nao le e nao grava segredo.</b> O {@code WHATSAPP_ACCESS_TOKEN} continua
 * exclusivamente no ambiente; o {@code GET} devolve apenas um booleano dizendo
 * se ele existe. Endpoint que devolve segredo transforma toda leitura em
 * vazamento — e o projeto nao tem autenticacao, por decisao de escopo. Sem
 * segredo aqui, essa decisao continua valendo.
 *
 * <h2>Por que junta duas fontes</h2>
 *
 * O servico (controle) sabe <b>gravar</b>; o {@link ConfiguracaoDoWhatsApp}
 * (borda) sabe o que esta <b>em vigor</b>, combinando banco e ambiente. Sao as
 * duas metades da resposta, e este controller e o unico ponto que pode falar com
 * as duas — motivo pelo qual a composicao acontece aqui, e nao em nenhum dos
 * dois lados.
 */
@RestController
@RequestMapping("/api/config/whatsapp")
public class WhatsAppConfigController {

    private final WhatsAppConfigService servico;
    private final ConfiguracaoDoWhatsApp configuracao;
    private final WhatsAppProperties props;

    public WhatsAppConfigController(
            WhatsAppConfigService servico,
            ConfiguracaoDoWhatsApp configuracao,
            WhatsAppProperties props) {
        this.servico = servico;
        this.configuracao = configuracao;
        this.props = props;
    }

    /** O que esta em vigor agora, com a origem de cada valor. */
    @GetMapping
    public WhatsAppConfigResponse ler() {
        return montar();
    }

    /**
     * Salva. Campo em branco devolve aquele valor ao {@code .env}.
     *
     * <p>{@code PUT} e nao {@code POST}: existe <b>uma</b> configuracao, sempre
     * no mesmo endereco. Mandar o estado inteiro duas vezes tem o mesmo efeito
     * que mandar uma.
     */
    @PutMapping
    public WhatsAppConfigResponse salvar(@Valid @RequestBody WhatsAppConfigRequest req) {
        servico.salvar(req);
        // Relê em vez de devolver o que foi salvo: o que interessa a quem chamou
        // e o que passou a valer, que pode diferir do enviado quando um campo em
        // branco devolve o controle ao ambiente.
        return montar();
    }

    /** Apaga a configuracao salva: voltam a valer as variaveis de ambiente. */
    @DeleteMapping
    public ResponseEntity<WhatsAppConfigResponse> restaurarAmbiente() {
        servico.restaurarAmbiente();
        return ResponseEntity.ok(montar());
    }

    private WhatsAppConfigResponse montar() {
        ConfiguracaoDoWhatsApp.Efetiva efetiva = configuracao.atual();
        boolean temToken = props.temToken();

        return new WhatsAppConfigResponse(
                efetiva.phoneNumberId(),
                efetiva.wabaId(),
                efetiva.templateName(),
                efetiva.templateLanguage(),
                efetiva.origem().name(),
                temToken,
                // "Pronto" exige as duas metades: token do ambiente e numero de
                // onde quer que venha. Uma so nao envia mensagem nenhuma.
                temToken && efetiva.identificada(),
                servico.carregar().map(WhatsAppConfig::getUpdatedAt).orElse(null));
    }
}
