package com.flightmonitor.core.alert.boundary.whatsapp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A API de configuracao do WhatsApp — etapa E4.7.
 *
 * <p>O teste mais importante daqui e o que verifica o que a API <b>nao</b> faz:
 * nao devolve segredo. E o que permite este projeto continuar sem autenticacao,
 * como decidido no escopo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WhatsAppConfigApiTest {

    @Autowired
    private MockMvc mvc;

    private static final String CORPO = """
            {
              "phoneNumberId": "123456789",
              "wabaId": "987654321",
              "templateName": "meu_template",
              "templateLanguage": "en_US"
            }
            """;

    @Test
    @DisplayName("GET diz o que esta em vigor e de onde veio")
    void leConfiguracao() throws Exception {
        mvc.perform(get("/api/config/whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").exists())
                .andExpect(jsonPath("$.origem").exists())
                .andExpect(jsonPath("$.tokenConfigurado").exists());
    }

    @Test
    @DisplayName("a resposta NUNCA traz o token")
    void naoVazaSegredo() throws Exception {
        // Endpoint que devolve segredo transforma toda leitura em vazamento — e
        // este projeto nao tem login, por decisao de escopo. Sem segredo aqui,
        // essa decisao continua de pe.
        String corpo = mvc.perform(get("/api/config/whatsapp"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo)
                .doesNotContain("accessToken")
                .doesNotContain("appSecret")
                .doesNotContain("webhookVerifyToken")
                .doesNotContain("token\":\"");
    }

    @Test
    @DisplayName("PUT salva e o GET seguinte ja reflete, sem reiniciar")
    void salvaEPassaAValer() throws Exception {
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("meu_template"))
                .andExpect(jsonPath("$.templateLanguage").value("en_US"))
                .andExpect(jsonPath("$.phoneNumberId").value("123456789"))
                // A origem passa a ser BANCO: e o que a tela usa para dizer que
                // o valor agora e editavel, e nao herdado do ambiente.
                .andExpect(jsonPath("$.origem").value("BANCO"));

        mvc.perform(get("/api/config/whatsapp"))
                .andExpect(jsonPath("$.templateName").value("meu_template"));
    }

    @Test
    @DisplayName("DELETE devolve o controle ao ambiente")
    void restauraAmbiente() throws Exception {
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(jsonPath("$.origem").value("BANCO"));

        // "Voltar ao padrao" precisa ser uma acao. Sem isto, seria apagar campo
        // por campo na tela e torcer para o vazio ser lido como ausencia.
        mvc.perform(delete("/api/config/whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("AMBIENTE"));
    }

    @Test
    @DisplayName("recusa nome de template fora do formato da Meta")
    void templateInvalido() throws Exception {
        // Maiuscula e hifen sao os erros comuns, e o sintoma seria 132001 no
        // primeiro alerta de verdade — que e quando menos se quer descobrir.
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateName": "Alerta-Preco"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.templateName", containsString("minusculas")));
    }

    @Test
    @DisplayName("recusa idioma com hifen, que e o engano classico")
    void idiomaComHifen() throws Exception {
        // pt-BR parece certo e devolve 132001, o MESMO erro de "template nao
        // existe" — o que faz procurar o problema no lugar errado.
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateLanguage": "pt-BR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.templateLanguage", containsString("sublinhado")));
    }

    @Test
    @DisplayName("recusa telefone no lugar do identificador da Meta")
    void telefoneNoLugarDoId() throws Exception {
        // O erro mais comum de quem configura pela primeira vez, e o guia avisa:
        // phone_number_id NAO e um telefone.
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumberId": "+5511999998888"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumberId", containsString("nao um telefone")));
    }

    @Test
    @DisplayName("corpo vazio aplica os padroes, em vez de estourar NOT NULL")
    void corpoVazio() throws Exception {
        mvc.perform(put("/api/config/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("alerta_preco_voo"))
                .andExpect(jsonPath("$.templateLanguage").value("pt_BR"));
    }
}
