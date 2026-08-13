import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/http'
import { mensagemDeErro, useCarregamento } from '@/composables/useCarregamento'

describe('useCarregamento', () => {
  it('guarda o dado e sai do estado de carregando', async () => {
    const { dados, carregando, erro, executar } = useCarregamento(async () => [1, 2, 3])

    await executar()

    expect(dados.value).toEqual([1, 2, 3])
    expect(carregando.value).toBe(false)
    expect(erro.value).toBeNull()
  })

  it('ja nasce carregando, para a tela nao piscar vazia', async () => {
    // Entre montar o componente e o `onMounted` disparar, `carregando: false`
    // deixaria um quadro sem dado, sem erro e sem "Carregando...".
    const { carregando } = useCarregamento(async () => 'ok')

    expect(carregando.value).toBe(true)
  })

  it('mostra a mensagem da API quando ela recusa', async () => {
    // O ProblemDetail do core traz texto escrito para humano; e ele que deve
    // aparecer na tela, e nao um "Erro inesperado" generico.
    const { erro, executar } = useCarregamento(async () => {
      throw new ApiError('Janela de ida invalida', 400)
    })

    await executar()

    expect(erro.value).toBe('Janela de ida invalida')
  })

  it('esconde erro tecnico atras da mensagem padrao', async () => {
    const { erro, executar } = useCarregamento(async () => {
      throw new TypeError('x.map is not a function')
    })

    await executar()

    expect(erro.value).toBe('Erro inesperado')
  })

  it('sempre sai de carregando, mesmo com erro', async () => {
    // O modo de falha que motivou o composable: um caminho de saida esquecer o
    // `finally` e a tela ficar em "Carregando..." para sempre.
    const { carregando, executar } = useCarregamento(async () => {
      throw new ApiError('caiu')
    })

    await executar()

    expect(carregando.value).toBe(false)
  })

  it('limpa o erro anterior ao tentar de novo', async () => {
    let falhar = true
    const { erro, executar } = useCarregamento(async () => {
      if (falhar) throw new ApiError('API fora do ar')
      return 'ok'
    })

    await executar()
    expect(erro.value).toBe('API fora do ar')

    falhar = false
    await executar()
    expect(erro.value).toBeNull()
  })

  it('nao lanca: o erro vira estado', async () => {
    const { executar } = useCarregamento(async () => {
      throw new ApiError('caiu')
    })

    await expect(executar()).resolves.toBeNull()
  })
})

describe('mensagemDeErro', () => {
  it('usa a mensagem da API quando existe', () => {
    expect(mensagemDeErro(new ApiError('Monitor nao encontrado', 404))).toBe(
      'Monitor nao encontrado',
    )
  })

  it('usa o padrao informado para qualquer outra coisa', () => {
    expect(mensagemDeErro(new Error('boom'), 'Erro ao excluir')).toBe('Erro ao excluir')
  })
})
