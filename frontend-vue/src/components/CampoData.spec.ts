import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CampoData from '@/components/CampoData.vue'

/**
 * O campo de data em dd/mm/aaaa.
 *
 * A conversao pura tem teste proprio em `lib/data.spec.ts`. Aqui se verifica a
 * ligacao: o que a pessoa VE, o que o formulario RECEBE, e que os dois nunca
 * saem de sincronia.
 */

function montar(valor: string | null = null) {
  return mount(CampoData, { props: { modelValue: valor } })
}

function texto(w: ReturnType<typeof montar>) {
  return (w.find('input[type="text"]').element as HTMLInputElement).value
}

async function digitar(w: ReturnType<typeof montar>, valor: string) {
  const campo = w.find('input[type="text"]')
  ;(campo.element as HTMLInputElement).value = valor
  await campo.trigger('input')
}

describe('CampoData', () => {
  it('mostra a data da API no formato nacional', () => {
    // O ponto da mudanca: a API manda ISO, a pessoa le dd/mm/aaaa.
    expect(texto(montar('2026-03-15'))).toBe('15/03/2026')
  })

  it('mostra vazio quando nao ha data, e nao "null"', () => {
    expect(texto(montar(null))).toBe('')
  })

  it('poe as barras enquanto se digita', async () => {
    const w = montar()
    await digitar(w, '15032026')

    expect(texto(w)).toBe('15/03/2026')
  })

  it('emite ISO para o formulario, e nao o texto da tela', async () => {
    // O resto do sistema (e a API) continua falando ISO. Se vazasse
    // dd/mm/aaaa daqui, o backend recusaria com erro de formato.
    const w = montar()
    await digitar(w, '15032026')

    expect(w.emitted('update:modelValue')?.at(-1)).toEqual(['2026-03-15'])
  })

  it('nao emite enquanto a data esta pela metade', async () => {
    const w = montar()
    await digitar(w, '1503')

    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('apagar tudo emite null', async () => {
    const w = montar('2026-03-15')
    await digitar(w, '')

    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([null])
  })

  it('data inexistente limpa o valor, em vez de virar 03/03', async () => {
    // 31/02 tem o formato certo e o dia errado. Sem a checagem, o `Date` do
    // JavaScript "corrigiria" para 03/03 em silencio.
    //
    // Emite `null`, e nao "nao emite": a primeira versao deste teste esperava
    // silencio, e silencio seria PIOR. O formulario ficaria com a data antiga
    // enquanto a tela mostra outra, e salvar guardaria o valor velho achando
    // que mudou. Data impossivel nao e valor; limpar diz isso.
    const w = montar('2026-03-15')
    await digitar(w, '31022026')

    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([null])
  })

  it('ao sair do campo, texto incompleto volta ao ultimo valor valido', async () => {
    const w = montar('2026-03-15')
    await digitar(w, '1503')
    await w.find('input[type="text"]').trigger('blur')

    // Melhor voltar do que guardar algo que a pessoa nao terminou de escrever
    // e vai achar que salvou.
    expect(texto(w)).toBe('15/03/2026')
  })

  it('acompanha mudanca vinda de fora', async () => {
    // Acontece de verdade: ao marcar "definir janela de volta", o formulario
    // preenche as datas sozinho.
    const w = montar(null)
    await w.setProps({ modelValue: '2027-01-10' })

    expect(texto(w)).toBe('10/01/2027')
  })

  it('mantem o seletor nativo disponivel', () => {
    // Trocar por campo de texto e perder o calendario seria consertar uma
    // coisa quebrando outra — pior ainda no celular.
    const w = montar()

    expect(w.find('input[type="date"]').exists()).toBe(true)
    expect(w.find('button.calendario').exists()).toBe(true)
  })

  it('escolher no calendario preenche o texto e emite ISO', async () => {
    const w = montar()
    const nativo = w.find('input[type="date"]')
    ;(nativo.element as HTMLInputElement).value = '2026-07-04'
    await nativo.trigger('change')

    expect(texto(w)).toBe('04/07/2026')
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual(['2026-07-04'])
  })
})
