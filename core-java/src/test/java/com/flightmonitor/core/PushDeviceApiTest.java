package com.flightmonitor.core;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/** O aplicativo registrando aparelhos, e a tela listando e apagando — D-113. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushDeviceApiTest {

    private static final String TOKEN = "fcm-token-de-teste-com-mais-de-vinte-caracteres";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EntityManager em;

    private void simularNovaRequisicao() {
        em.flush();
        em.clear();
    }

    private Long destinatario(String nome, String telefone) throws Exception {
        String corpo = mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"phoneE164\": \"%s\"}".formatted(nome, telefone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private String registrar(Long recipientId, String token, String nome) throws Exception {
        return mvc.perform(post("/api/recipients/{id}/push-devices", recipientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"name\": \"%s\"}".formatted(token, nome)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("registra o aparelho e o devolve na ficha do destinatario, sem o token")
    void registraEListaSemToken() throws Exception {
        Long id = destinatario("Leonardo", "+5511944441111");

        registrar(id, TOKEN, "Pixel 8");
        simularNovaRequisicao();

        mvc.perform(get("/api/recipients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushDevices", hasSize(1)))
                .andExpect(jsonPath("$.pushDevices[0].name").value("Pixel 8"))
                .andExpect(jsonPath("$.pushDevices[0].active").value(true))
                .andExpect(jsonPath("$.pushDevices[0].token").doesNotExist());

        mvc.perform(get("/api/recipients"))
                .andExpect(jsonPath("$[0].pushDevices", hasSize(1)));
    }

    @Test
    @DisplayName("registrar o mesmo token de novo atualiza, e pode trocar de dono")
    void registrarDeNovoNaoDuplica() throws Exception {
        Long leonardo = destinatario("Leonardo", "+5511944442222");
        Long maria = destinatario("Maria", "+5511944443333");

        String primeiro = registrar(leonardo, TOKEN, "Pixel 8");
        simularNovaRequisicao();
        String segundo = registrar(maria, TOKEN, "Pixel 8 da Maria");
        simularNovaRequisicao();

        // O mesmo registro, e nao um segundo: o celular receberia em dobro.
        org.assertj.core.api.Assertions.assertThat(json.readTree(segundo).get("id").asLong())
                .isEqualTo(json.readTree(primeiro).get("id").asLong());
        mvc.perform(get("/api/recipients/{id}/push-devices", leonardo))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/recipients/{id}/push-devices", maria))
                .andExpect(jsonPath("$[0].name").value("Pixel 8 da Maria"));
    }

    @Test
    @DisplayName("token curto demais e recusado com 400")
    void validaToken() throws Exception {
        Long id = destinatario("Leonardo", "+5511944444444");

        mvc.perform(post("/api/recipients/{id}/push-devices", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"curto\", \"name\": \"Pixel\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("destinatario inexistente da 404")
    void destinatarioInexistente() throws Exception {
        mvc.perform(post("/api/recipients/{id}/push-devices", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"name\": \"Pixel\"}".formatted(TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE remove o aparelho; apagar o destinatario leva os aparelhos junto")
    void exclusao() throws Exception {
        Long id = destinatario("Leonardo", "+5511944445555");
        Long aparelho = json.readTree(registrar(id, TOKEN, "Pixel 8")).get("id").asLong();
        registrar(id, TOKEN + "-tablet", "Tablet");
        simularNovaRequisicao();

        mvc.perform(delete("/api/push-devices/{id}", aparelho)).andExpect(status().isNoContent());
        simularNovaRequisicao();
        mvc.perform(get("/api/recipients/{id}/push-devices", id)).andExpect(jsonPath("$", hasSize(1)));
        simularNovaRequisicao();

        mvc.perform(delete("/api/recipients/{id}", id)).andExpect(status().isNoContent());
        simularNovaRequisicao();
        org.assertj.core.api.Assertions.assertThat(
                em.createQuery("select count(d) from PushDevice d", Long.class).getSingleResult())
                .isZero();
    }
}
