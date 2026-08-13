import { describe, expect, it } from 'vitest'

import type { WhatsAppConfig } from '@/model/configuracao'
import { paraEnvio, paraRequest } from '@/model/configuracao'

function config(parcial: Partial<WhatsAppConfig> = {}): WhatsAppConfig {
  return {
    phoneNumberId: '123',
    wabaId: '456',
    templateName: 'alerta_preco_voo',
    templateLanguage: 'pt_BR',
    origem: 'AMBIENTE',
    tokenConfigurado: true,
    pronto: true,
    updatedAt: null,
    ...parcial,
  }
}

describe('paraEnvio', () => {
  it('campo apagado vira nulo — e isso devolve o valor ao .env', () => {
    // Aqui o vazio tem significado de negocio, e nao e so higiene de formulario:
    // apagar o campo e como o usuario diz "volta a valer o ambiente".
    const enviado = paraEnvio({
      phoneNumberId: '',
      wabaId: '   ',
      templateName: 'meu_template',
      templateLanguage: 'pt_BR',
    })

    expect(enviado.phoneNumberId).toBeNull()
    expect(enviado.wabaId).toBeNull()
  })

  it('tira espacos em volta dos valores preenchidos', () => {
    const enviado = paraEnvio({
      phoneNumberId: ' 123 ',
      wabaId: null,
      templateName: ' meu_template ',
      templateLanguage: ' pt_BR ',
    })

    expect(enviado.phoneNumberId).toBe('123')
    expect(enviado.templateName).toBe('meu_template')
    expect(enviado.templateLanguage).toBe('pt_BR')
  })
})

describe('paraRequest', () => {
  it('nulo vira string vazia, que e o que o input entende', () => {
    // `<input v-model>` com null mostraria "null" na caixa de texto.
    const req = paraRequest(config({ phoneNumberId: null, wabaId: null }))

    expect(req.phoneNumberId).toBe('')
    expect(req.wabaId).toBe('')
  })

  it('preserva o que veio do ambiente, para o usuario poder so ajustar', () => {
    // Quem abre a tela pela primeira vez ve os valores do .env ja preenchidos —
    // e nao um formulario em branco que parece perder a configuracao atual.
    const req = paraRequest(config({ origem: 'AMBIENTE', templateName: 'do_ambiente' }))

    expect(req.templateName).toBe('do_ambiente')
  })
})
