import { del, getJson, postJson, putJson } from '@/api/http'
import type { PushDevice, Recipient, RecipientRequest } from '@/model/recipient'

/** O recurso `/recipients` do core-java: quem recebe os alertas. */

export function listarDestinatarios(): Promise<Recipient[]> {
  return getJson<Recipient[]>('/recipients')
}

export function buscarDestinatario(id: number): Promise<Recipient> {
  return getJson<Recipient>(`/recipients/${id}`)
}

export function criarDestinatario(req: RecipientRequest): Promise<Recipient> {
  return postJson<Recipient>('/recipients', req)
}

export function atualizarDestinatario(id: number, req: RecipientRequest): Promise<Recipient> {
  return putJson<Recipient>(`/recipients/${id}`, req)
}

export function excluirDestinatario(id: number): Promise<void> {
  return del(`/recipients/${id}`)
}

// ---------------------------------------------- aparelhos do destinatario (D-113)

/**
 * Registra este aparelho para o destinatario. Chamado pelo painel rodando no
 * aplicativo, com o token que o app pediu ao Firebase. Registrar de novo o
 * mesmo token atualiza — inclusive trocando de destinatario.
 */
export function registrarAparelho(
  recipientId: number,
  aparelho: { token: string; name: string },
): Promise<PushDevice> {
  return postJson<PushDevice>(`/recipients/${recipientId}/push-devices`, aparelho)
}
