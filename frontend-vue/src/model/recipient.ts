/** Espelha os DTOs de destinatario do core-java. */

import type { CanalDeNotificacao } from '@/model/health'

/**
 * Um aparelho com o aplicativo Android, que recebe pelo canal PUSH (D-113).
 * Sem o token: ele so serve a quem envia, que e o core.
 */
export interface PushDevice {
  id: number
  recipientId: number
  name: string
  active: boolean
  /** Por que foi desligado, quando o Firebase recusou o token. */
  lastError: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface Recipient {
  id: number
  name: string
  /** Nulo desde a E4.6: um destinatario pode receber so por e-mail. */
  phoneE164: string | null
  /** Nulo quando o destinatario so recebe por WhatsApp. */
  email: string | null
  active: boolean
  createdAt: string | null
  updatedAt: string | null
  /** Aparelhos com o aplicativo. Cadastrados pelo app, nao pelo formulario. */
  pushDevices: PushDevice[]
}

/**
 * Payload de criacao e atualizacao.
 *
 * Telefone e e-mail sao opcionais individualmente, mas **pelo menos um** e
 * obrigatorio — a API recusa com 400 e a mensagem no campo `phoneE164`. A regra
 * mora la, e nao aqui: validacao no navegador e conveniencia, nao garantia.
 */
export interface RecipientRequest {
  name: string
  phoneE164: string | null
  email: string | null
  active: boolean
}

export function destinatarioVazio(): RecipientRequest {
  return { name: '', phoneE164: '', email: '', active: true }
}

export function paraRequest(r: Recipient): RecipientRequest {
  return {
    name: r.name,
    phoneE164: r.phoneE164 ?? '',
    email: r.email ?? '',
    active: r.active,
  }
}

/**
 * Campo em branco vira nulo antes de ir para a API.
 *
 * O formulario produz `""` para campo nao preenchido, e `""` nao e ausencia: o
 * backend ate trata isso (RecipientRequest converte vazio em nulo), mas mandar
 * o que se quer dizer e melhor do que depender de o outro lado adivinhar.
 */
export function paraEnvio(req: RecipientRequest): RecipientRequest {
  return {
    name: req.name.trim(),
    phoneE164: vazioViraNulo(req.phoneE164),
    email: vazioViraNulo(req.email),
    active: req.active,
  }
}

/** O contato a exibir: os que existirem, na ordem em que se lê. */
export function contatosDe(r: Recipient): string {
  return [r.phoneE164, r.email].filter(Boolean).join(' · ')
}

/** Este destinatario pode ser alcancado por este canal? Espelha AlcanceDosCanais. */
export function alcancavelPor(r: Recipient, canal: CanalDeNotificacao): boolean {
  if (canal === 'EMAIL') return Boolean(r.email)
  if (canal === 'WHATSAPP') return Boolean(r.phoneE164)
  if (canal === 'PUSH') return (r.pushDevices ?? []).some((d) => d.active)
  return true
}

/**
 * Nenhum dos canais ativos chega a esta pessoa.
 *
 * E o caso que importa avisar: com varios canais, faltar UM contato e normal —
 * quem nao tem o app recebe por e-mail. Faltar todos e alerta perdido.
 */
export function inalcancavel(r: Recipient, canais: CanalDeNotificacao[]): boolean {
  return canais.length > 0 && !canais.some((c) => alcancavelPor(r, c))
}

/** O que falta cadastrar para o canal alcancar alguem, em portugues. */
export function contatoDoCanal(canal: CanalDeNotificacao): string {
  switch (canal) {
    case 'EMAIL':
      return 'e-mail'
    case 'WHATSAPP':
      return 'telefone'
    case 'PUSH':
      return 'aparelho com o aplicativo'
    case 'LOG':
      return 'nada'
  }
}

function vazioViraNulo(valor: string | null): string | null {
  return valor === null || valor.trim() === '' ? null : valor.trim()
}
