import { ref, type Ref } from 'vue'

import { ApiError } from '@/api/http'

/**
 * O estado de uma chamada a API: carregando, erro, ou o dado.
 *
 * # Por que isto existe
 *
 * O bloco abaixo estava copiado em quatro lugares (MonitoresView,
 * HistoricoView, StatusPanel, MonitorForm):
 *
 * ```
 * carregando.value = true
 * erro.value = null
 * try { ... } catch (e) {
 *   erro.value = e instanceof ApiError ? e.message : 'Erro inesperado'
 * } finally { carregando.value = false }
 * ```
 *
 * Copia de tratamento de erro e onde o `finally` some. Basta um caminho de
 * saida esquecer de zerar `carregando` para a tela ficar em "Carregando..."
 * para sempre, sem nada no console — o modo de falha mais chato de diagnosticar
 * justamente porque parece que a API esta lenta.
 */
export interface Carregamento<T> {
  /** O dado da ultima chamada bem-sucedida. Nulo antes da primeira. */
  dados: Ref<T | null>
  /** Verdadeiro so enquanto uma chamada esta em voo. */
  carregando: Ref<boolean>
  /** A mensagem de erro da ultima chamada, ja traduzida para o usuario. */
  erro: Ref<string | null>
  /** Executa a chamada. Nunca lanca — o erro vira estado. */
  executar: () => Promise<T | null>
}

/**
 * @param buscar a chamada a API
 * @param mensagemPadrao o que dizer quando o erro nao veio da API (bug nosso,
 *        JSON invalido): a mensagem tecnica nao ajudaria quem le a tela
 */
export function useCarregamento<T>(
  buscar: () => Promise<T>,
  mensagemPadrao = 'Erro inesperado',
): Carregamento<T> {
  const dados = ref<T | null>(null) as Ref<T | null>
  // Comeca em `true`, e nao em `false`: as tres telas chamam no `onMounted`, e
  // entre montar e a primeira chamada haveria um quadro sem dado, sem erro e
  // sem "Carregando..." — a tela piscaria vazia.
  const carregando = ref(true)
  const erro = ref<string | null>(null)

  async function executar(): Promise<T | null> {
    carregando.value = true
    erro.value = null
    try {
      dados.value = await buscar()
      return dados.value
    } catch (e) {
      // Erro da API tem mensagem escrita para humano (ProblemDetail.detail);
      // qualquer outra coisa e defeito nosso, e o texto interno so assustaria.
      erro.value = e instanceof ApiError ? e.message : mensagemPadrao
      return null
    } finally {
      carregando.value = false
    }
  }

  return { dados, carregando, erro, executar }
}

/**
 * A mesma traducao de erro, para acoes que nao tem dado de retorno a guardar
 * (excluir, disparar varredura) e por isso nao justificam um `Carregamento`.
 */
export function mensagemDeErro(e: unknown, mensagemPadrao = 'Erro inesperado'): string {
  return e instanceof ApiError ? e.message : mensagemPadrao
}
