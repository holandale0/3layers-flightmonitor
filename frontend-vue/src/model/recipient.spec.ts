import { describe, expect, it } from 'vitest'

import type { Recipient } from '@/model/recipient'
import { alcancavelPor, contatosDe, destinatarioVazio, paraEnvio, paraRequest } from '@/model/recipient'

function destinatario(parcial: Partial<Recipient> = {}): Recipient {
  return {
    id: 1,
    name: 'Maria',
    phoneE164: '+5511999998888',
    email: 'maria@exemplo.com',
    active: true,
    createdAt: null,
    updatedAt: null,
    ...parcial,
  }
}

describe('paraEnvio', () => {
  it('converte campo em branco para nulo', () => {
    // O formulario produz "" para campo nao preenchido, e "" nao e ausencia.
    // O backend ate trata isso, mas mandar o que se quer dizer e melhor do que
    // depender de o outro lado adivinhar.
    const enviado = paraEnvio({ name: 'Maria', phoneE164: '', email: '   ', active: true })

    expect(enviado.phoneE164).toBeNull()
    expect(enviado.email).toBeNull()
  })

  it('tira espacos em volta do que foi preenchido', () => {
    const enviado = paraEnvio({
      name: '  Maria  ',
      phoneE164: ' +5511999998888 ',
      email: ' maria@exemplo.com ',
      active: true,
    })

    expect(enviado.name).toBe('Maria')
    expect(enviado.phoneE164).toBe('+5511999998888')
    expect(enviado.email).toBe('maria@exemplo.com')
  })
})

describe('alcancavelPor', () => {
  it('so com e-mail nao e alcancavel por WhatsApp', () => {
    // E o caso que a tela precisa avisar: o alerta viraria falha permanente, e
    // a pessoa nunca saberia que perdeu a passagem.
    const soEmail = destinatario({ phoneE164: null })

    expect(alcancavelPor(soEmail, 'WHATSAPP')).toBe(false)
    expect(alcancavelPor(soEmail, 'EMAIL')).toBe(true)
  })

  it('so com telefone nao e alcancavel por e-mail', () => {
    const soTelefone = destinatario({ email: null })

    expect(alcancavelPor(soTelefone, 'EMAIL')).toBe(false)
    expect(alcancavelPor(soTelefone, 'WHATSAPP')).toBe(true)
  })

  it('o canal LOG alcanca qualquer um', () => {
    // O LOG so imprime: serve qualquer contato que identifique a pessoa.
    expect(alcancavelPor(destinatario({ phoneE164: null }), 'LOG')).toBe(true)
    expect(alcancavelPor(destinatario({ email: null }), 'LOG')).toBe(true)
  })
})

describe('contatosDe', () => {
  it('mostra so o que existe, sem "null" na tela', () => {
    expect(contatosDe(destinatario({ email: null }))).toBe('+5511999998888')
    expect(contatosDe(destinatario({ phoneE164: null }))).toBe('maria@exemplo.com')
    expect(contatosDe(destinatario())).toBe('+5511999998888 · maria@exemplo.com')
  })
})

describe('paraRequest', () => {
  it('nulo vira string vazia, que e o que o input entende', () => {
    // `<input v-model>` com null mostraria "null" na caixa de texto.
    const req = paraRequest(destinatario({ phoneE164: null, email: null }))

    expect(req.phoneE164).toBe('')
    expect(req.email).toBe('')
  })
})

describe('destinatarioVazio', () => {
  it('nasce ativo, porque quem cadastra quer receber', () => {
    expect(destinatarioVazio().active).toBe(true)
  })
})
