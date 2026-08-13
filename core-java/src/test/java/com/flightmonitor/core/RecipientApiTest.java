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

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipientApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EntityManager em;

    /**
     * Em producao cada requisicao tem seu proprio contexto de persistencia. Como
     * o teste roda tudo numa transacao so, e preciso limpar o contexto para
     * reproduzir esse isolamento — sobretudo quando o banco faz cascata que o
     * Hibernate desconhece.
     */
    private void simularNovaRequisicao() {
        em.flush();
        em.clear();
    }

    private String payload(String nome, String telefone) {
        return """
                {"name": "%s", "phoneE164": "%s"}
                """.formatted(nome, telefone);
    }

    private Long criar(String nome, String telefone) throws Exception {
        String corpo = mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(nome, telefone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    @Test
    @DisplayName("POST cria o destinatario com 201 e cabecalho Location")
    void cria() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Leonardo", "+5511911112222")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/recipients/")))
                .andExpect(jsonPath("$.name").value("Leonardo"))
                .andExpect(jsonPath("$.phoneE164").value("+5511911112222"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("telefone com espacos, hifen e parenteses e normalizado")
    void normalizaTelefone() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Formatado", "+55 (11) 91111-3333")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneE164").value("+5511911113333"));
    }

    @Test
    @DisplayName("telefone sem o + do E.164 e recusado, e nao adivinhado")
    void recusaTelefoneSemPais() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Sem pais", "11911114444")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneE164", containsString("E.164")));
    }

    @Test
    @DisplayName("nome em branco devolve 400")
    void recusaNomeVazio() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("   ", "+5511911115555")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("telefone duplicado devolve 409, e nao 500 do banco")
    void recusaDuplicado() throws Exception {
        criar("Primeiro", "+5511911116666");

        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Segundo", "+5511911116666")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail", containsString("+5511911116666")));
    }

    @Test
    @DisplayName("duplicidade e detectada mesmo com formatacao diferente")
    void duplicadoAposNormalizacao() throws Exception {
        criar("Primeiro", "+5511911117777");

        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Mesmo numero", "+55 11 91111-7777")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET por id devolve o destinatario; id inexistente devolve 404")
    void buscaPorId() throws Exception {
        Long id = criar("Leonardo", "+5511911118888");

        mvc.perform(get("/api/recipients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(get("/api/recipients/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));
    }

    @Test
    @DisplayName("PUT mantendo o proprio telefone nao acusa conflito")
    void atualizaMantendoTelefone() throws Exception {
        Long id = criar("Nome antigo", "+5511911119999");

        mvc.perform(put("/api/recipients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Nome novo", "+5511911119999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome novo"));
    }

    @Test
    @DisplayName("PUT com telefone de outro destinatario devolve 409")
    void atualizaComTelefoneDeOutro() throws Exception {
        criar("Dono do numero", "+5511922221111");
        Long id = criar("Outro", "+5511922222222");

        mvc.perform(put("/api/recipients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Outro", "+5511922221111")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("outro destinatario")));
    }

    @Test
    @DisplayName("PUT pode desativar sem apagar o cadastro")
    void desativa() throws Exception {
        Long id = criar("Leonardo", "+5511922223333");

        mvc.perform(put("/api/recipients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Leonardo", "phoneE164": "+5511922223333", "active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        String ativos = mvc.perform(get("/api/recipients").param("active", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(ativos).doesNotContain("+5511922223333");
    }

    @Test
    @DisplayName("DELETE remove o destinatario e o GET seguinte devolve 404")
    void exclui() throws Exception {
        Long id = criar("Temporario", "+5511922224444");

        mvc.perform(delete("/api/recipients/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/recipients/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("apagar destinatario desfaz o vinculo mas preserva o monitor")
    void exclusaoNaoDerrubaMonitor() throws Exception {
        Long idDestinatario = criar("Vinculado", "+5511922225555");

        LocalDate ida = LocalDate.now().plusYears(1);
        String monitor = """
                {
                  "label": "Com destinatario",
                  "origin": "GRU", "destination": "LIS",
                  "departureWindowStart": "%s",
                  "departureWindowEnd": "%s",
                  "maxPrice": 3200.00,
                  "recipientIds": [%d]
                }
                """.formatted(ida, ida.plusDays(10), idDestinatario);

        String criado = mvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(monitor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipients", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Long idMonitor = json.readTree(criado).get("id").asLong();
        simularNovaRequisicao();

        mvc.perform(delete("/api/recipients/{id}", idDestinatario))
                .andExpect(status().isNoContent());
        simularNovaRequisicao();

        // O monitor continua existindo, apenas sem aquele destinatario: quem
        // sumiu foi a linha de monitor_recipient, pela cascata do banco.
        mvc.perform(get("/api/monitors/{id}", idMonitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients", hasSize(0)));
    }

    // =========================================================================
    // E4.6 — o destinatario ganhou e-mail, e o telefone deixou de ser obrigatorio
    // =========================================================================

    @Test
    @DisplayName("cria destinatario so com e-mail, sem telefone")
    void criaSoComEmail() throws Exception {
        // O caso que motivou a E4.6: quem so recebe por e-mail. Antes, exigir um
        // telefone produziria um numero inventado — que um dia receberia
        // mensagem de verdade.
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "So Email", "email": "so.email@exemplo.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("so.email@exemplo.com"))
                .andExpect(jsonPath("$.phoneE164").doesNotExist());
    }

    @Test
    @DisplayName("recusa destinatario sem telefone E sem e-mail")
    void recusaSemNenhumContato() throws Exception {
        // O estado sem sentido: uma pessoa cadastrada para receber alertas, sem
        // nenhuma forma de recebe-los.
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ninguem"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneE164",
                        containsString("telefone ou e-mail")));
    }

    @Test
    @DisplayName("campo vazio conta como ausente, e nao como contato")
    void vazioNaoEhContato() throws Exception {
        // Formulario manda "" e nao null. Sem tratar, a string vazia passaria
        // pelo "tem algum contato" e viraria um contato que nao alcanca ninguem.
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Vazio", "phoneE164": "", "email": "  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o e-mail e' guardado em minusculas")
    void emailEmMinusculas() throws Exception {
        // Sem isto, "Leo@x.com" e "leo@x.com" passariam pelo indice unico como
        // duas pessoas, e a mesma caixa receberia o alerta duas vezes.
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Caixa Alta", "email": "Leo.Araujo@Exemplo.COM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("leo.araujo@exemplo.com"));
    }

    @Test
    @DisplayName("e-mail repetido e' conflito, e nao erro de banco")
    void emailDuplicado() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Primeiro", "email": "repetido@exemplo.com"}
                                """))
                .andExpect(status().isCreated());
        simularNovaRequisicao();

        // Sem a checagem no servico, o indice unico do banco estouraria e o
        // usuario veria uma mensagem de constraint num formulario.
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Segundo", "email": "repetido@exemplo.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("repetido@exemplo.com")));
    }

    @Test
    @DisplayName("varios destinatarios sem e-mail nao disputam unicidade")
    void variosSemEmail() throws Exception {
        // No PostgreSQL varios NULLs convivem num indice unico. Se a checagem do
        // servico nao ignorasse o nulo, o segundo cadastro sem e-mail seria
        // recusado como duplicado — e o WhatsApp pararia de aceitar gente nova.
        criar("Um", "+5511911110001");
        simularNovaRequisicao();
        criar("Dois", "+5511911110002");
    }

    @Test
    @DisplayName("recusa e-mail obviamente invalido")
    void emailInvalido() throws Exception {
        mvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Torto", "email": "nao-e-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }
}
