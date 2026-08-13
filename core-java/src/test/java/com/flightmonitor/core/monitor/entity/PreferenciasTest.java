package com.flightmonitor.core.monitor.entity;

import com.flightmonitor.core.monitor.entity.Monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Casamento de nome de companhia — etapa E2.6.
 *
 * <p>Parece detalhe e é a parte que mais podia falhar em silêncio: a camada 1
 * devolve o código IATA (`IB`) e a camada 2 devolve o nome por extenso
 * (`Iberia`). Uma comparação literal deixaria metade das ofertas passar, e o
 * usuário veria a preferência funcionando às vezes.
 */
class PreferenciasTest {

    @Nested
    @DisplayName("o mesmo nome, escrito de dois jeitos")
    class DoisJeitos {

        @Test
        @DisplayName("o código IATA exclui também o nome por extenso")
        void codigoExcluiNome() {
            Set<String> evitadas = Set.of("IB");

            assertThat(Preferencias.companhiaEvitada("IB", evitadas)).isTrue();
            // É este o caso que a comparação literal deixaria passar: a camada 2
            // grava "Iberia", e o usuário digitou o código.
            assertThat(Preferencias.companhiaEvitada("Iberia", evitadas)).isTrue();
        }

        @Test
        @DisplayName("o nome por extenso exclui também o código")
        void nomeExcluiCodigo() {
            Set<String> evitadas = Set.of("Iberia");

            assertThat(Preferencias.companhiaEvitada("Iberia", evitadas)).isTrue();
            // Sem a simetria, a preferência funcionaria ou não dependendo de
            // qual camada encontrou a oferta — o pior tipo de comportamento,
            // porque parece aleatório.
            assertThat(Preferencias.companhiaEvitada("IB", evitadas)).isTrue();
        }

        @Test
        @DisplayName("maiúscula, minúscula e espaço nas pontas não mudam nada")
        void normalizacao() {
            assertThat(Preferencias.companhiaEvitada("  iberia ", Set.of("IBERIA"))).isTrue();
            assertThat(Preferencias.companhiaEvitada("IBERIA", Set.of(" iberia "))).isTrue();
        }

        @Test
        @DisplayName("a camada 2 pode devolver várias companhias juntas")
        void variasCompanhiasNaMesmaOferta() {
            // É o formato real do fast-flights em voo com conexão de operadoras
            // diferentes.
            String observada = "Tap Air Portugal, Iberia";

            assertThat(Preferencias.companhiaEvitada(observada, Set.of("Iberia"))).isTrue();
            assertThat(Preferencias.companhiaEvitada(observada, Set.of("Tap"))).isTrue();
            assertThat(Preferencias.companhiaEvitada(observada, Set.of("LATAM"))).isFalse();
        }

        @Test
        @DisplayName("codigo que nao prefixa o nome nao alcanca — e isso e sabido")
        void codigoSemRelacaoTextual() {
            // "TP" e o codigo IATA da TAP, e textualmente nao ha relacao: "TAP"
            // nao comeca com "TP". Alcancar este caso exigiria uma tabela de
            // codigos IATA, que este projeto nao tem motivo para carregar.
            assertThat(Preferencias.companhiaEvitada("Tap Air Portugal", Set.of("TP"))).isFalse();
            // O nome resolve.
            assertThat(Preferencias.companhiaEvitada("Tap Air Portugal", Set.of("TAP"))).isTrue();
        }
    }

    @Nested
    @DisplayName("o que NÃO pode ser excluído por engano")
    class SemFalsoPositivo {

        @Test
        @DisplayName("companhia diferente não é excluída")
        void companhiaDiferente() {
            Set<String> evitadas = Set.of("IB", "Gol");

            assertThat(Preferencias.companhiaEvitada("LATAM", evitadas)).isFalse();
            assertThat(Preferencias.companhiaEvitada("Azul", evitadas)).isFalse();
        }

        @Test
        @DisplayName("o nome curto alcança a razão social inteira")
        void nomeCurtoAlcancaRazaoSocial() {
            // É o caso mais natural de todos, e a primeira versão não cobria:
            // a pessoa digita o nome que conhece, a fonte devolve o nome longo.
            assertThat(Preferencias.companhiaEvitada("Azul Linhas Aereas", Set.of("Azul")))
                    .isTrue();
            assertThat(Preferencias.companhiaEvitada("Air France", Set.of("Air"))).isTrue();
        }

        @Test
        @DisplayName("prefixo no meio do nome não conta")
        void prefixoNoMeioNaoConta() {
            // "Aereas" aparece no nome, mas não como primeira palavra. Casar
            // qualquer trecho transformaria a preferência numa busca por
            // substring, e "Air" excluiria "Fly Air Brasil".
            assertThat(Preferencias.companhiaEvitada("Azul Linhas Aereas", Set.of("Aereas")))
                    .isFalse();
        }

        @Test
        @DisplayName("prefixo curto não vaza para outra companhia com o mesmo começo")
        void prefixoCurtoNaoVaza() {
            // "AZUL" tem quatro letras, então a regra de código curto não se
            // aplica — e sem palavra inteira, não casa.
            assertThat(Preferencias.companhiaEvitada("Azulao Airways", Set.of("Azul"))).isFalse();
        }

        @Test
        @DisplayName("lista vazia ou companhia nula não excluem nada")
        void semListaOuSemDado() {
            assertThat(Preferencias.companhiaEvitada("Iberia", Set.of())).isFalse();
            assertThat(Preferencias.companhiaEvitada("Iberia", null)).isFalse();
            // Observação da camada 1 pode vir sem companhia; isso não pode
            // virar exclusão.
            assertThat(Preferencias.companhiaEvitada(null, Set.of("IB"))).isFalse();
            assertThat(Preferencias.companhiaEvitada("   ", Set.of("IB"))).isFalse();
        }
    }

    @Test
    @DisplayName("a entidade guarda já normalizado, como o CHECK do banco exige")
    void entidadeNormalizaAoGuardar() {
        Monitor m = new Monitor();

        m.evitarCompanhia("  iberia ");
        m.evitarCompanhia("tp");
        // Vazio não vira linha: o banco recusaria, e o erro apareceria longe
        // daqui.
        m.evitarCompanhia("   ");
        m.evitarCompanhia(null);

        assertThat(m.getAvoidedAirlines()).containsExactlyInAnyOrder("IBERIA", "TP");
    }
}
