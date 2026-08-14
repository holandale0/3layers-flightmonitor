package com.flightmonitor.core;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;

// Spring Boot 4 usa Jackson 3: o pacote mudou de com.fasterxml.jackson
// para tools.jackson. Ver D-013.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Testes da API de monitores, ponta a ponta contra o PostgreSQL real.
 *
 * <p>{@code @Transactional} garante rollback ao fim de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MonitorApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RecipientRepository destinatarios;

    /** Datas sempre no futuro: o DTO recusa monitorar data que ja passou. */
    private final LocalDate ida = LocalDate.now().plusYears(1);

    private String payloadValido() {
        return """
                {
                  "label": "Lisboa",
                  "origin": "GRU",
                  "destination": "LIS",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "minStayDays": 10,
                  "maxStayDays": 15,
                  "maxPrice": 3200.00,
                  "maxStops": 1
                }
                """.formatted(ida, ida.plusDays(10));
    }

    private Long criarMonitor() throws Exception {
        String corpo = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    @Test
    @DisplayName("POST cria o monitor, devolve 201 e o cabecalho Location")
    void criaMonitor() throws Exception {
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/monitors/")))
                .andExpect(jsonPath("$.origin").value("GRU"))
                .andExpect(jsonPath("$.destination").value("LIS"))
                .andExpect(jsonPath("$.maxPrice").value(3200.00))
                // Defaults aplicados pelo DTO, espelhando os DEFAULT do banco
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.passengers").value(1))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.searchIntervalMinutes").value(360))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.recipients", hasSize(0)));
    }

    @Test
    @DisplayName("IATA em minusculas e normalizado para maiusculas")
    void normalizaIata() throws Exception {
        String corpo = payloadValido().replace("\"GRU\"", "\"gru\"");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("GRU"));
    }

    @Test
    @DisplayName("campos obrigatorios ausentes devolvem 400 com o detalhe por campo")
    void rejeitaPayloadIncompleto() throws Exception {
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Payload invalido"))
                .andExpect(jsonPath("$.errors.origin").exists())
                .andExpect(jsonPath("$.errors.destination").exists())
                .andExpect(jsonPath("$.errors.maxPrice").exists());
    }

    @Test
    @DisplayName("IATA com formato invalido devolve 400 explicando o formato")
    void rejeitaIataInvalido() throws Exception {
        String corpo = payloadValido().replace("\"GRU\"", "\"GRUX\"");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.origin", containsString("IATA")));
    }

    @Test
    @DisplayName("preco-teto zerado devolve 400")
    void rejeitaPrecoZerado() throws Exception {
        String corpo = payloadValido().replace("3200.00", "0");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxPrice").exists());
    }

    @Test
    @DisplayName("data de partida no passado devolve 400")
    void rejeitaDataPassada() throws Exception {
        String corpo = payloadValido()
                .replace(ida.toString(), LocalDate.now().minusDays(1).toString());

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.departureWindowStart").exists());
    }

    @Test
    @DisplayName("janela de ida invertida devolve 400 apontando o campo certo")
    void rejeitaJanelaInvertida() throws Exception {
        String corpo = """
                {
                  "origin": "GRU", "destination": "LIS",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 3200.00
                }
                """.formatted(ida.plusDays(10), ida);

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.departureWindowEnd").exists());
    }

    @Test
    @DisplayName("janela de volta pela metade devolve 400")
    void rejeitaJanelaDeVoltaIncompleta() throws Exception {
        String corpo = """
                {
                  "origin": "GRU", "destination": "LIS",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "returnWindowStart": "%s",
                  "maxPrice": 3200.00
                }
                """.formatted(ida, ida.plusDays(10), ida.plusDays(20));

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.returnWindowEnd").exists());
    }

    @Test
    @DisplayName("permanencia minima maior que a maxima devolve 400")
    void rejeitaPermanenciaInvertida() throws Exception {
        String corpo = payloadValido()
                .replace("\"minStayDays\": 10", "\"minStayDays\": 20");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxStayDays").exists());
    }

    @Test
    @DisplayName("origem igual ao destino devolve 409")
    void rejeitaRotaDegenerada() throws Exception {
        String corpo = payloadValido().replace("\"LIS\"", "\"GRU\"");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("origem e destino")));
    }

    @Test
    @DisplayName("GET por id devolve o monitor; id inexistente devolve 404")
    void buscaPorId() throws Exception {
        Long id = criarMonitor();

        mvc.perform(get("/api/monitors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(get("/api/monitors/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));
    }

    @Test
    @DisplayName("GET com ?active=true filtra apenas os ativos")
    void filtraAtivos() throws Exception {
        Long id = criarMonitor();

        String desativado = payloadValido().replace("\"label\": \"Lisboa\"",
                "\"label\": \"Desligado\", \"active\": false");
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(desativado))
                .andExpect(status().isCreated());

        String corpo = mvc.perform(get("/api/monitors").param("active", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = json.readTree(corpo);
        for (JsonNode m : lista) {
            org.assertj.core.api.Assertions.assertThat(m.get("active").asBoolean()).isTrue();
        }
        org.assertj.core.api.Assertions.assertThat(lista.toString()).contains(id.toString());
        org.assertj.core.api.Assertions.assertThat(lista.toString()).doesNotContain("Desligado");
    }

    @Test
    @DisplayName("PUT atualiza os campos do monitor")
    void atualiza() throws Exception {
        Long id = criarMonitor();
        String corpo = payloadValido()
                .replace("3200.00", "2500.00")
                .replace("\"Lisboa\"", "\"Lisboa mais barato\"");

        mvc.perform(put("/api/monitors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPrice").value(2500.00))
                .andExpect(jsonPath("$.label").value("Lisboa mais barato"));
    }

    @Test
    @DisplayName("PUT em id inexistente devolve 404")
    void atualizaInexistente() throws Exception {
        mvc.perform(put("/api/monitors/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE remove o monitor e o GET seguinte devolve 404")
    void exclui() throws Exception {
        Long id = criarMonitor();

        mvc.perform(delete("/api/monitors/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/monitors/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("vincula destinatarios existentes e rejeita id inexistente")
    void vinculaDestinatarios() throws Exception {
        Recipient r = destinatarios.saveAndFlush(new Recipient("Leonardo", "+5511977776666"));

        String comDestinatario = payloadValido()
                .replace("\"maxStops\": 1", "\"maxStops\": 1, \"recipientIds\": [%d]".formatted(r.getId()));

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comDestinatario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipients", hasSize(1)))
                .andExpect(jsonPath("$.recipients[0].phoneE164").value("+5511977776666"));

        String comIdInvalido = payloadValido()
                .replace("\"maxStops\": 1", "\"maxStops\": 1, \"recipientIds\": [999999]");

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comIdInvalido))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("Destinatario")));
    }

    // ------------------------------------------------ preferencias (E2.6)

    @Test
    @DisplayName("as preferencias voltam na resposta, normalizadas")
    void preferenciasIdaEVolta() throws Exception {
        String payload = """
                {
                  "label": "Madri",
                  "origin": "GRU",
                  "destination": "MAD",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 4000.00,
                  "prefereVooDireto": true,
                  "avoidedAirlines": ["  iberia ", "tp"],
                  "pesoEscalas": 60,
                  "pesoHorario": 0
                }
                """.formatted(ida, ida.plusDays(10));

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prefereVooDireto").value(true))
                // Guardadas em maiuscula e sem espaco, como o CHECK do banco
                // exige, e devolvidas em ordem estavel.
                .andExpect(jsonPath("$.avoidedAirlines[0]").value("IBERIA"))
                .andExpect(jsonPath("$.avoidedAirlines[1]").value("TP"))
                .andExpect(jsonPath("$.pesoEscalas").value(60))
                // Zero e escolha valida: "nao me importo com horario".
                .andExpect(jsonPath("$.pesoHorario").value(0))
                // Nulo e diferente: "nao escolhi, use o global".
                .andExpect(jsonPath("$.pesoPreco").doesNotExist());
    }

    @Test
    @DisplayName("a lista enviada substitui a anterior, para dar como remover")
    void listaDeCompanhiasESubstituida() throws Exception {
        String comDuas = payloadComCompanhias("[\"IB\", \"TP\"]");
        String resposta = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comDuas))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(resposta).get("id").asLong();

        mvc.perform(put("/api/monitors/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComCompanhias("[\"IB\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avoidedAirlines", hasSize(1)))
                .andExpect(jsonPath("$.avoidedAirlines[0]").value("IB"));
    }

    @Test
    @DisplayName("zerar todos os pesos e recusado, com mensagem em vez de erro de banco")
    void todosOsPesosZeradosERecusado() throws Exception {
        String payload = """
                {
                  "label": "sem peso nenhum",
                  "origin": "GRU",
                  "destination": "MAD",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 4000.00,
                  "pesoPreco": 0, "pesoEscalas": 0, "pesoDuracao": 0, "pesoHorario": 0
                }
                """.formatted(ida, ida.plusDays(10));

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("peso")));
    }

    @Test
    @DisplayName("peso acima de 100 e recusado pela validacao")
    void pesoForaDaFaixa() throws Exception {
        String payload = """
                {
                  "label": "peso absurdo",
                  "origin": "GRU",
                  "destination": "MAD",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 4000.00,
                  "pesoPreco": 500
                }
                """.formatted(ida, ida.plusDays(10));

        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pesoPreco").exists());
    }

    private String payloadComCompanhias(String lista) {
        return """
                {
                  "label": "companhias",
                  "origin": "GRU",
                  "destination": "MAD",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 4000.00,
                  "avoidedAirlines": %s
                }
                """.formatted(ida, ida.plusDays(10), lista);
    }

    // =========================================================================
    // Intervalo minimo de varredura (10 min) — protege as fontes gratuitas
    // =========================================================================

    @Test
    @DisplayName("recusa intervalo abaixo de 10 minutos")
    void intervaloAbaixoDoMinimo() throws Exception {
        // O limite existe porque as duas fontes sao gratuitas e nao
        // contratadas, e porque a camada 1 devolve dado CACHEADO: varrer mais
        // rapido que o cache atualiza gasta cota para reler o mesmo preco.
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComIntervalo(9)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.searchIntervalMinutes",
                        containsString("10 minutos")));
    }

    @Test
    @DisplayName("aceita exatamente 10 minutos")
    void intervaloNoLimite() throws Exception {
        // O limite e inclusivo. Testar so o valor recusado deixaria passar um
        // `>` no lugar de `>=` sem ninguem notar.
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComIntervalo(10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.searchIntervalMinutes").value(10));
    }

    @Test
    @DisplayName("recusa tambem na edicao, e nao so na criacao")
    void intervaloAbaixoDoMinimoAoEditar() throws Exception {
        // Uma regra que vale so na criacao e uma regra que se contorna: bastava
        // criar com 10 e editar para 1.
        String corpo = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComIntervalo(60)))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(corpo).get("id").asLong();

        mvc.perform(put("/api/monitors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadComIntervalo(1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sem intervalo informado, vale o padrao de 6 horas")
    void intervaloPadrao() throws Exception {
        mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin": "GRU", "destination": "LIS",
                                  "departureWindowStart": "2027-03-10",
                                  "departureWindowEnd": "2027-03-20",
                                  "maxPrice": 3200
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.searchIntervalMinutes").value(360));
    }

    private String payloadComIntervalo(int minutos) {
        return """
                {
                  "origin": "GRU", "destination": "LIS",
                  "departureWindowStart": "2027-03-10",
                  "departureWindowEnd": "2027-03-20",
                  "maxPrice": 3200,
                  "searchIntervalMinutes": %d
                }
                """.formatted(minutos);
    }
}
