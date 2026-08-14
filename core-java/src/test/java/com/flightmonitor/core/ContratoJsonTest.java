package com.flightmonitor.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * O contrato JSON: campo sem valor viaja como {@code null}, e nao sumido.
 *
 * <h2>Por que este teste existe</h2>
 *
 * Nasceu do <b>BUG-015</b>. A configuracao
 * {@code spring.jackson.default-property-inclusion: non_null} fazia a API
 * <b>omitir</b> as propriedades nulas. Um monitor sem limite de escalas chegava
 * ao painel sem a chave {@code maxStops}.
 *
 * <p>O tipo do frontend sempre declarou {@code maxStops: number | null}. Era a
 * API que mentia — e em JavaScript campo ausente e {@code undefined}, com
 * {@code undefined === null} valendo {@code false}. O sintoma visivel foi
 * "ate undefined escala" na tela; o invisivel, e pior, foi o formulario de
 * edicao marcar "janela de volta" num monitor de somente ida e transformar um
 * no outro ao salvar.
 *
 * <h2>Ausente e ambiguo; nulo nao e</h2>
 *
 * Campo que sumiu pode significar tres coisas — nao existe, nao se aplica, ou
 * alguem esqueceu. {@code null} explicito significa uma so: este campo nao tem
 * valor. E o mesmo principio que o projeto aplica em toda parte
 * ({@code SEM_DADOS != NORMAL}, nulo != zero), agora no formato do fio.
 *
 * <p>O ganho de bytes de omitir nulo nunca pagou o custo de um cliente ter que
 * adivinhar o que a ausencia queria dizer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContratoJsonTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static final String SOMENTE_IDA = """
            {
              "origin": "CGH",
              "destination": "BEL",
              "departureWindowStart": "2026-12-01",
              "departureWindowEnd": "2026-12-20",
              "maxPrice": 700
            }
            """;

    @Test
    @DisplayName("monitor sem escalas maximas devolve maxStops: null, e nao omite a chave")
    void nuloViajaExplicito() throws Exception {
        String corpo = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON).content(SOMENTE_IDA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode monitor = json.readTree(corpo);

        // `has` e a asserção que importa: `get` devolveria null nos dois casos,
        // e o teste passaria com o bug presente.
        assertThat(monitor.has("maxStops"))
                .as("a chave maxStops precisa existir mesmo sem valor: "
                        + "ausente vira undefined no cliente (BUG-015)")
                .isTrue();
        assertThat(monitor.get("maxStops").isNull()).isTrue();
    }

    @Test
    @DisplayName("as chaves de janela de volta existem num monitor de somente ida")
    void janelaDeVoltaAusenteAindaAparece() throws Exception {
        String corpo = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON).content(SOMENTE_IDA))
                .andReturn().getResponse().getContentAsString();

        JsonNode monitor = json.readTree(corpo);

        // Estes tres produziram o pior sintoma do BUG-015: com a chave ausente,
        // o formulario marcava "definir janela de volta" e preenchia as datas
        // sozinho, convertendo o monitor ao salvar.
        for (String campo : new String[] {"returnWindowStart", "returnWindowEnd", "minStayDays"}) {
            assertThat(monitor.has(campo)).as("chave " + campo).isTrue();
            assertThat(monitor.get(campo).isNull()).as("valor de " + campo).isTrue();
        }
    }

    @Test
    @DisplayName("a listagem segue a mesma regra da criacao")
    void listagemTambem() throws Exception {
        mvc.perform(post("/api/monitors")
                .contentType(MediaType.APPLICATION_JSON).content(SOMENTE_IDA));

        mvc.perform(get("/api/monitors"))
                .andExpect(status().isOk())
                // Sem isso, criar e listar poderiam divergir — e a tela usa a
                // listagem, que foi justamente onde o bug apareceu.
                .andExpect(jsonPath("$[0].maxStops").doesNotExist())
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("maxStops")));
    }

    @Test
    @DisplayName("destinatario sem e-mail devolve email: null")
    void destinatarioTambem() throws Exception {
        String corpo = mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "So Telefone", "phoneE164": "+5511977776666"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corpo).has("email")).isTrue();
    }
}
