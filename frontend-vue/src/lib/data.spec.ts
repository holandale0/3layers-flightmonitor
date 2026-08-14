import { describe, expect, it } from 'vitest'

import { brParaIso, completa, isoParaBr, mascarar } from '@/lib/data'

describe('isoParaBr', () => {
  it('converte a data da API para o formato nacional', () => {
    expect(isoParaBr('2026-03-15')).toBe('15/03/2026')
  })

  it('nulo e ausente viram string vazia, e nao "null"', () => {
    // Um monitor de somente ida nao tem janela de volta. O campo fica em
    // branco, e nao mostrando a palavra "null" — foi a licao do BUG-015.
    expect(isoParaBr(null)).toBe('')
    expect(isoParaBr(undefined)).toBe('')
    expect(isoParaBr('')).toBe('')
  })

  it('nao tenta adivinhar formato desconhecido', () => {
    expect(isoParaBr('15/03/2026')).toBe('')
    expect(isoParaBr('2026-3-5')).toBe('')
  })
})

describe('brParaIso', () => {
  it('converte o que a pessoa digitou para o que a API espera', () => {
    expect(brParaIso('15/03/2026')).toBe('2026-03-15')
  })

  it('rejeita data que nao existe, em vez de "corrigir" em silencio', () => {
    // `new Date(2026, 1, 31)` vira 03/03 sem reclamar. A pessoa procuraria
    // voo num dia que nao pediu, e nada na tela diria isso.
    expect(brParaIso('31/02/2026')).toBeNull()
    expect(brParaIso('31/04/2026')).toBeNull()
    expect(brParaIso('00/03/2026')).toBeNull()
    expect(brParaIso('15/13/2026')).toBeNull()
  })

  it('conhece bissexto, inclusive a regra do seculo', () => {
    expect(brParaIso('29/02/2028')).toBe('2028-02-29')
    expect(brParaIso('29/02/2027')).toBeNull()
    // 2100 e divisivel por 4 e NAO e bissexto. Uma janela de datas longa
    // alcanca anos assim, e a regra simples erraria.
    expect(brParaIso('29/02/2100')).toBeNull()
    expect(brParaIso('29/02/2000')).toBe('2000-02-29')
  })

  it('incompleto e nulo viram null', () => {
    expect(brParaIso('15/03')).toBeNull()
    expect(brParaIso('')).toBeNull()
    expect(brParaIso(null)).toBeNull()
  })

  it('ida e volta: o que sai da API volta igual', () => {
    const iso = '2027-12-31'
    expect(brParaIso(isoParaBr(iso))).toBe(iso)
  })
})

describe('mascarar', () => {
  it('poe as barras enquanto se digita', () => {
    expect(mascarar('1')).toBe('1')
    expect(mascarar('15')).toBe('15')
    expect(mascarar('153')).toBe('15/3')
    expect(mascarar('1503')).toBe('15/03')
    expect(mascarar('15032026')).toBe('15/03/2026')
  })

  it('ignora o que nao for digito, inclusive barra digitada a mao', () => {
    // Quem digita as barras nao pode acabar com "15//03".
    expect(mascarar('15/03/2026')).toBe('15/03/2026')
    expect(mascarar('15-03-2026')).toBe('15/03/2026')
    expect(mascarar('abc15')).toBe('15')
  })

  it('para de aceitar depois de oito digitos', () => {
    expect(mascarar('150320261234')).toBe('15/03/2026')
  })

  it('apagar funciona: menos digitos, menos barras', () => {
    expect(mascarar('15/0')).toBe('15/0')
    expect(mascarar('15/')).toBe('15')
  })
})

describe('completa', () => {
  it('so e completa com os oito digitos', () => {
    expect(completa('15/03/2026')).toBe(true)
    expect(completa('15/03/202')).toBe(false)
    expect(completa('')).toBe(false)
  })
})
