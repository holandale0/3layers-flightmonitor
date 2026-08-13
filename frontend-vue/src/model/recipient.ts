/** Espelha os DTOs de destinatario do core-java. */

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

/** Este destinatario pode ser alcancado pelo canal ativo? */
export function alcancavelPor(r: Recipient, canal: 'WHATSAPP' | 'EMAIL' | 'LOG'): boolean {
  if (canal === 'EMAIL') return Boolean(r.email)
  if (canal === 'WHATSAPP') return Boolean(r.phoneE164)
  return true
}

function vazioViraNulo(valor: string | null): string | null {
  return valor === null || valor.trim() === '' ? null : valor.trim()
}
