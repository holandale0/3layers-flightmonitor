/** Espelha os DTOs de configuracao do canal WhatsApp — etapa E4.7. */

/** De onde veio o valor que esta em vigor. */
export type OrigemDaConfiguracao = 'BANCO' | 'AMBIENTE'

export interface WhatsAppConfig {
  phoneNumberId: string | null
  wabaId: string | null
  templateName: string
  templateLanguage: string
  origem: OrigemDaConfiguracao
  /**
   * Se ha token no ambiente. **Booleano, nunca o valor** — a API não devolve
   * segredo, e a tela não precisa dele para dizer o que falta.
   */
  tokenConfigurado: boolean
  /** Token no ambiente **e** número conhecido. Uma metade só não envia nada. */
  pronto: boolean
  updatedAt: string | null
}

export interface WhatsAppConfigRequest {
  phoneNumberId: string | null
  wabaId: string | null
  templateName: string
  templateLanguage: string
}

export function paraRequest(c: WhatsAppConfig): WhatsAppConfigRequest {
  return {
    phoneNumberId: c.phoneNumberId ?? '',
    wabaId: c.wabaId ?? '',
    templateName: c.templateName,
    templateLanguage: c.templateLanguage,
  }
}

/**
 * Campo em branco vira nulo — e aqui isso tem significado de negocio:
 * apagar um campo **devolve aquele valor ao `.env`**.
 */
export function paraEnvio(req: WhatsAppConfigRequest): WhatsAppConfigRequest {
  return {
    phoneNumberId: vazioViraNulo(req.phoneNumberId),
    wabaId: vazioViraNulo(req.wabaId),
    templateName: req.templateName.trim(),
    templateLanguage: req.templateLanguage.trim(),
  }
}

function vazioViraNulo(valor: string | null): string | null {
  return valor === null || valor.trim() === '' ? null : valor.trim()
}
