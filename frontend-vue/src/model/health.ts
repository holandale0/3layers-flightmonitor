/** Resposta do /actuator/health do core-java. */

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthComponent {
  status: HealthStatus
  details?: Record<string, unknown>
}

export interface HealthResponse {
  status: HealthStatus
  components?: Record<string, HealthComponent> & {
    /** Indicador `notificacao` do core: qual canal esta ativo agora. */
    notificacao?: HealthComponent & { details?: DetalhesDaNotificacao }
  }
}

/** Detalhes do indicador `notificacao`. Espelha NotificacaoHealthIndicator. */
export interface DetalhesDaNotificacao {
  canal: CanalDeNotificacao
  disponiveis: CanalDeNotificacao[]
  /** O alerta espera confirmacao externa? Verdadeiro no WhatsApp, falso no e-mail. */
  confirmacaoAssincrona: boolean
}

export type CanalDeNotificacao = 'WHATSAPP' | 'EMAIL' | 'LOG'
