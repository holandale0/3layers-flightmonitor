/** Resposta do /actuator/health do core-java. */

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthComponent {
  status: HealthStatus
  details?: Record<string, unknown>
}

export interface HealthResponse {
  status: HealthStatus
  components?: Record<string, HealthComponent> & {
    /** Indicador `notificacao` do core: quais canais estao ativos agora. */
    notificacao?: HealthComponent & { details?: DetalhesDaNotificacao }
  }
}

/** Detalhes do indicador `notificacao`. Espelha NotificacaoHealthIndicator. */
export interface DetalhesDaNotificacao {
  /** Ativos ao mesmo tempo (D-113), na ordem configurada em NOTIFICATION_CHANNELS. */
  canais: CanalDeNotificacao[]
  disponiveis: CanalDeNotificacao[]
  /** Ativos sem implementacao no core. Presente so quando ha algum — e o status cai. */
  semImplementacao?: CanalDeNotificacao[]
  /** O alerta espera confirmacao externa? Verdadeiro se o WhatsApp estiver entre os ativos. */
  confirmacaoAssincrona: boolean
}

export type CanalDeNotificacao = 'WHATSAPP' | 'EMAIL' | 'PUSH' | 'LOG'
