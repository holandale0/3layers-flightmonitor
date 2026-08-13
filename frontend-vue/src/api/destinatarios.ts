import { del, getJson, postJson, putJson } from '@/api/http'
import type { Recipient, RecipientRequest } from '@/model/recipient'

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
