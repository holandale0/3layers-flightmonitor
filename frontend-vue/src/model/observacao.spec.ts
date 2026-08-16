import { describe, expect, it } from 'vitest'

import type { Observacao } from '@/model/observacao'
import { ondeComprar } from '@/model/observacao'

function obs(parcial: Partial<Observacao> = {}): Observacao {
  return {
    id: 1,
    origin: 'CGH',
    destination: 'BEL',
    departureDate: '2026-12-19',
    returnDate: null,
    price: 1383,
    currency: 'BRL',
    airline: null,
    agency: null,
    stops: 1,
    durationMinutes: 425,
    departureAt: null,
    arrivalAt: null,
    source: 'TRAVELPAYOUTS',
    confirmed: false,
    observedAt: '2026-08-16T13:57:49Z',
    ...parcial,
  }
}

describe('ondeComprar', () => {
  it('mostra a companhia quando a fonte a informa', () => {
    expect(ondeComprar(obs({ airline: 'LATAM' }))).toEqual({
      valor: 'LATAM',
      tipo: 'companhia',
    })
  })

  it('mostra a agencia quando so ela existe', () => {
    // O caso do endpoint de so ida: ele nao diz quem opera o voo, mas diz
    // onde comprar — e essa e a informacao que permite agir.
    expect(ondeComprar(obs({ agency: 'Kiwi.com' }))).toEqual({
      valor: 'Kiwi.com',
      tipo: 'agência',
    })
  })

  it('a companhia tem precedencia sobre a agencia', () => {
    // Quem opera o voo e mais informativo do que quem intermediou a venda.
    expect(ondeComprar(obs({ airline: 'GOL', agency: 'Kiwi.com' })!)).toEqual({
      valor: 'GOL',
      tipo: 'companhia',
    })
  })

  it('sem nenhum dos dois, nao inventa referencia', () => {
    expect(ondeComprar(obs())).toBeNull()
  })

  it('o rotulo diz qual das duas coisas e', () => {
    // Sem o rotulo, "Kiwi.com" na coluna Companhia voltaria a afirmar algo
    // falso — foi exatamente esse o erro que a separacao veio corrigir.
    expect(ondeComprar(obs({ agency: 'Mytrip.com' }))!.tipo).toBe('agência')
    expect(ondeComprar(obs({ airline: 'LA' }))!.tipo).toBe('companhia')
  })
})
