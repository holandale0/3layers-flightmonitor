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
