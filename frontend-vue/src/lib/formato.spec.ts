import { describe, expect, it } from 'vitest'

import { data, dataCurta, dinheiro, duracao, instante } from '@/lib/formato'

/**
 * Estas funcoes existiam em triplicata antes da reorganizacao, e as copias ja
 * tinham divergido. Os testes abaixo fixam as diferencas que sao intencionais —
 * para a proxima copia nao nascer por engano.
 */

describe('dinheiro', () => {
  it('formata real com simbolo e separadores locais', () => {
    //   e o espaco NAO separavel que o Intl usa depois do "R$".
    expect(dinheiro(3720.5)).toBe('R$ 3.720,50')
  })

  it('devolve travessao para nulo, e nao zero', () => {
    // Nao ter preco e diferente de o preco ser zero — a mesma regra do backend.
    expect(dinheiro(null)).toBe('—')
    expect(dinheiro(undefined)).toBe('—')
    expect(dinheiro(0)).toBe('R$ 0,00')
  })

  it('nao aplica formato brasileiro a outra moeda', () => {
    // "USD 1.234,00" leria como mil e duzentos para um americano.
    expect(dinheiro(320, 'USD')).toBe('USD 320')
  })

  it('arredonda quando pedido, para o rotulo do grafico', () => {
    expect(dinheiro(3720.5, 'BRL', true)).toBe('R$ 3.721')
    expect(dinheiro(320.4, 'USD', true)).toBe('USD 320')
  })
})

describe('data', () => {
  it('nao volta um dia por causa do fuso', () => {
    // Sem o T00:00:00, `new Date('2026-03-15')` e lido como UTC e, no horario
    // de Brasilia (-03), vira 14/03. Este teste e a razao de a funcao existir.
    expect(data('2026-03-15')).toBe('15/03/2026')
  })

  it('devolve travessao quando nao ha data', () => {
    expect(data(null)).toBe('—')
  })
})

describe('dataCurta', () => {
  it('mostra dia e mes, que e o que cabe no eixo', () => {
    expect(dataCurta('2026-03-15')).toBe('15/03')
  })
})

describe('instante', () => {
  it('devolve travessao quando o monitor nunca foi varrido', () => {
    // `lastSearchedAt` e nulo ate a primeira busca.
    expect(instante(null)).toBe('—')
  })

  it('formata um instante com fuso', () => {
    // Comparar com string fixa dependeria do fuso da maquina; o que importa e
    // que saia data E hora, e nao a data crua do ISO.
    const texto = instante('2026-03-15T14:30:00Z')
    expect(texto).toMatch(/^\d{2}\/\d{2}\/\d{4},? \d{2}:\d{2}:\d{2}$/)
  })
})

describe('duracao', () => {
  it('converte minutos em horas e minutos', () => {
    // Ninguem pensa em duracao de viagem em minutos.
    expect(duracao(990)).toBe('16h30')
    expect(duracao(425)).toBe('7h05')
  })

  it('hora cheia nao mostra os minutos', () => {
    expect(duracao(120)).toBe('2h')
  })

  it('menos de uma hora fica em minutos', () => {
    expect(duracao(45)).toBe('45min')
  })

  it('sem duracao vira travessao, e nao 0h00', () => {
    // O calendario de ida e volta nao informa duracao. Nao saber e diferente
    // de durar zero.
    expect(duracao(null)).toBe('—')
    expect(duracao(undefined)).toBe('—')
    expect(duracao(0)).toBe('—')
  })

  it('valor quebrado nao vira duracao negativa na tela', () => {
    expect(duracao(-30)).toBe('—')
  })
})
