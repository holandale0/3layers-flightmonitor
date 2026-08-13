import { getJson } from '@/api/http'
import type { Recipient } from '@/model/recipient'

/** O recurso `/recipients` do core-java: quem recebe os alertas. */

export function listarDestinatarios(): Promise<Recipient[]> {
  return getJson<Recipient[]>('/recipients')
}
