import { describe, expect, it } from 'vitest'

import { resumirVarreduras, type Varredura } from './varredura'

function varredura(parcial: Partial<Varredura>): Varredura {
  return {
    id: 1,
    source: 'TRAVELPAYOUTS',
    status: 'SUCCESS',
    startedAt: '2026-10-28T10:00:00Z',
    finishedAt: '2026-10-28T10:00:01Z',
    observationsCount: 0,
    errorMessage: null,
    ...parcial,
  }
}

describe('resumirVarreduras', () => {
  it('sem varreduras, nada a resumir', () => {
    expect(resumirVarreduras([])).toEqual({
      ciclos: 0,
      falhas: 0,
      primeira: null,
      ultima: null,
      ultimoErro: null,
      consultasAoVivo: 0,
    })
  })

  it('conta ciclos pela camada 1, e as consultas ao vivo a parte', () => {
    const r = resumirVarreduras([
      varredura({ id: 4, startedAt: '2026-10-28T12:00:00Z' }),
      varredura({ id: 3, source: 'FAST_FLIGHTS', startedAt: '2026-10-28T11:00:01Z' }),
      varredura({ id: 2, source: 'FAST_FLIGHTS', startedAt: '2026-10-28T11:00:02Z' }),
      varredura({ id: 1, startedAt: '2026-10-28T11:00:00Z' }),
    ])

    expect(r.ciclos).toBe(2)
    expect(r.consultasAoVivo).toBe(2)
    // A lista vem da mais nova para a mais antiga.
    expect(r.ultima).toBe('2026-10-28T12:00:00Z')
    expect(r.primeira).toBe('2026-10-28T11:00:00Z')
  })

  it('o erro mostrado e o da falha mais recente', () => {
    const r = resumirVarreduras([
      varredura({ id: 3, status: 'FAILED', errorMessage: 'worker fora do ar' }),
      varredura({ id: 2 }),
      varredura({ id: 1, status: 'FAILED', errorMessage: 'timeout antigo' }),
    ])

    expect(r.falhas).toBe(2)
    expect(r.ultimoErro).toBe('worker fora do ar')
  })
})
