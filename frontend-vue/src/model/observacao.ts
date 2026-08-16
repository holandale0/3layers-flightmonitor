/** Espelha ObservationResponse do core-java. */
export interface Observacao {
  id: number
  origin: string
  destination: string
  departureDate: string
  returnDate: string | null
  price: number
  currency: string
  /** Quem OPERA o voo. Comparado com as companhias evitadas do monitor. */
  airline: string | null
  /** Quem VENDE a passagem (Kiwi.com, Mytrip.com). E a referencia para comprar. */
  agency: string | null
  stops: number | null
  durationMinutes: number | null
  departureAt: string | null
  arrivalAt: string | null
  source: 'TRAVELPAYOUTS' | 'FAST_FLIGHTS'
  confirmed: boolean
  observedAt: string
}

/** Uma barra do grafico: o menor preco ja visto para uma data de partida. */
export interface MenorPorData {
  data: string
  preco: number
  confirmado: boolean
  observacoes: number
}

/**
 * Reduz as observacoes ao menor preco por data de partida.
 *
 * O grafico responde "qual dia esta mais barato", entao varias observacoes da
 * mesma data viram uma barra — a melhor. O historico completo fica na tabela.
 */
export function menorPrecoPorData(obs: Observacao[]): MenorPorData[] {
  const porData = new Map<string, MenorPorData>()

  for (const o of obs) {
    const atual = porData.get(o.departureDate)
    if (!atual) {
      porData.set(o.departureDate, {
        data: o.departureDate,
        preco: o.price,
        confirmado: o.confirmed,
        observacoes: 1,
      })
      continue
    }
    atual.observacoes++
    if (o.price < atual.preco) {
      atual.preco = o.price
      atual.confirmado = o.confirmed
    }
  }

  return [...porData.values()].sort((a, b) => a.data.localeCompare(b.data))
}

/**
 * Quem procurar para comprar esta passagem.
 *
 * As duas fontes da camada 1 sabem metades diferentes: o calendario de ida e
 * volta diz a **companhia** e nao diz a agencia; o de so ida diz a **agencia**
 * e nao diz a companhia. Elas ficam separadas no banco de proposito — o campo
 * `airline` e comparado com as companhias que o monitor evita, e agencia ali
 * quebraria a regra em silencio.
 *
 * Mas quem le a tabela quer saber uma coisa so: *onde eu compro isso?* Entao na
 * tela elas aparecem juntas, com o rótulo dizendo qual das duas e.
 */
export function ondeComprar(o: Observacao): { valor: string; tipo: 'companhia' | 'agência' } | null {
  if (o.airline) return { valor: o.airline, tipo: 'companhia' }
  if (o.agency) return { valor: o.agency, tipo: 'agência' }
  return null
}
