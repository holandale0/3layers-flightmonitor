import { getActuator } from '@/api/http'
import type { HealthResponse } from '@/model/health'

/**
 * A saude do sistema, pelo Actuator do core-java.
 *
 * Fica fora de `/api` de proposito: o Actuator responde na raiz, e o proxy do
 * Vite trata `/actuator` como um caminho a parte (ver `vite.config.ts`).
 */

export function verificarSaude(): Promise<HealthResponse> {
  return getActuator<HealthResponse>('/health')
}
