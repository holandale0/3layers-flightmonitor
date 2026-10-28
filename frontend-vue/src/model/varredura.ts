/** Espelha SearchRunResponse do core-java. */
export interface Varredura {
  id: number
  /** TRAVELPAYOUTS e a camada 1 (cache); FAST_FLIGHTS e a camada 2 (Google, ao vivo). */
  source: 'TRAVELPAYOUTS' | 'FAST_FLIGHTS'
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
  startedAt: string
  finishedAt: string | null
  observationsCount: number
  errorMessage: string | null
}

export interface ResumoDasVarreduras {
  /** Varreduras da camada 1 — uma por ciclo do monitor. */
  ciclos: number
  falhas: number
  primeira: string | null
  ultima: string | null
  ultimoErro: string | null
  /** Consultas diretas ao Google (reserva ou confirmacao). */
  consultasAoVivo: number
}

/**
 * O que as varreduras dizem quando o historico esta vazio.
 *
 * Sem isto a tela so sabia dizer "use Buscar agora" — igual para um monitor
 * que nunca rodou e para um que rodou 20 vezes sem a fonte ter preco. Sao
 * situacoes opostas, e so uma delas se resolve buscando de novo.
 *
 * Espera a lista da mais nova para a mais antiga, como a API devolve.
 */
export function resumirVarreduras(varreduras: Varredura[]): ResumoDasVarreduras {
  const ciclos = varreduras.filter((v) => v.source === 'TRAVELPAYOUTS')
  const falhas = ciclos.filter((v) => v.status === 'FAILED')

  return {
    ciclos: ciclos.length,
    falhas: falhas.length,
    primeira: ciclos.at(-1)?.startedAt ?? null,
    ultima: ciclos[0]?.startedAt ?? null,
    ultimoErro: falhas[0]?.errorMessage ?? null,
    consultasAoVivo: varreduras.filter((v) => v.source === 'FAST_FLIGHTS').length,
  }
}
