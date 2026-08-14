import { describe, expect, it } from 'vitest'

import type { Monitor } from '@/model/monitor'
import { paraRequest } from '@/model/monitor'

/**
 * O BUG-015 e a diferenca entre "campo nulo" e "campo ausente".
 *
 * A API omitia propriedades nulas (`spring.jackson.default-property-inclusion:
 * non_null`), entao um monitor sem escalas maximas chegava **sem a chave**
 * `maxStops` — e nao com `maxStops: null`. Em JavaScript isso e `undefined`, e
 * `undefined === null` e falso.
 *
 * O tipo `Monitor` sempre declarou `maxStops: number | null`. Era a API que
 * mentia, e o frontend acreditava.
 */

/** Um monitor como a API o entregava: sem as chaves dos campos nulos. */
function monitorSomenteIda(): Monitor {
  return {
    id: 1,
    label: 'CGH para BEL',
    origin: 'CGH',
    destination: 'BEL',
    departureWindowStart: '2026-12-01',
    departureWindowEnd: '2026-12-20',
    maxPrice: 700,
    currency: 'BRL',
    passengers: 1,
    active: true,
    searchIntervalMinutes: 60,
    recipients: [],
    // As chaves abaixo estao AUSENTES de proposito, reproduzindo a resposta
    // real da API para um monitor de somente ida sem limite de escalas.
  } as unknown as Monitor
}

describe('paraRequest com campos ausentes na resposta', () => {
  it('normaliza campo ausente para null, e nao deixa passar undefined', () => {
    // Era o que produzia "ate undefined escala" na tela: o valor chegava
    // undefined e toda comparacao com null falhava silenciosamente.
    const req = paraRequest(monitorSomenteIda())

    expect(req.maxStops).toBeNull()
    expect(req.returnWindowStart).toBeNull()
    expect(req.returnWindowEnd).toBeNull()
    expect(req.minStayDays).toBeNull()
    expect(req.maxStayDays).toBeNull()
  })

  it('nao inventa janela de volta num monitor de somente ida', () => {
    // O sintoma grave: com `undefined`, o formulario marcava "definir janela de
    // volta", e o watch preenchia as datas sozinho. Editar um monitor de
    // somente ida o transformava em ida e volta ao salvar.
    const req = paraRequest(monitorSomenteIda())

    expect(req.returnWindowStart).toBeNull()
    expect(req.minStayDays).toBeNull()
  })

  it('preserva os valores que existem', () => {
    const comTudo = { ...monitorSomenteIda(), maxStops: 1, minStayDays: 5, maxStayDays: 10 }

    const req = paraRequest(comTudo as Monitor)

    expect(req.maxStops).toBe(1)
    expect(req.minStayDays).toBe(5)
  })

  it('escala zero sobrevive, porque zero e um valor', () => {
    // `maxStops: 0` significa "voo direto", e nao "sem preferencia". Uma
    // normalizacao descuidada com `||` transformaria um no outro.
    const direto = { ...monitorSomenteIda(), maxStops: 0 }

    expect(paraRequest(direto as Monitor).maxStops).toBe(0)
  })
})
