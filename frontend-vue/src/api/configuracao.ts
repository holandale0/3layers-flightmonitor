import { del, getJson, putJson } from '@/api/http'
import type { WhatsAppConfig, WhatsAppConfigRequest } from '@/model/configuracao'

/**
 * Configuracao do canal WhatsApp: `/config/whatsapp`.
 *
 * Nao ha `criar`: existe UMA configuracao, sempre no mesmo endereco. Que ela
 * ainda nao tenha linha no banco e detalhe de armazenamento, e nao algo que a
 * tela precise saber.
 */

export function lerConfigWhatsApp(): Promise<WhatsAppConfig> {
  return getJson<WhatsAppConfig>('/config/whatsapp')
}

export function salvarConfigWhatsApp(req: WhatsAppConfigRequest): Promise<WhatsAppConfig> {
  return putJson<WhatsAppConfig>('/config/whatsapp', req)
}

/** Apaga o que foi salvo: voltam a valer as variaveis de ambiente. */
export function restaurarAmbiente(): Promise<WhatsAppConfig> {
  return del<WhatsAppConfig>('/config/whatsapp')
}
