import { del, postJson } from '@/api/http'

/**
 * O recurso `/push-devices` do core-java: um aparelho ja registrado (D-113).
 * O registro em si mora em destinatarios.ts, porque nasce sob o destinatario.
 */

export function removerAparelho(id: number): Promise<void> {
  return del(`/push-devices/${id}`)
}

/** Uma notificacao de diagnostico, sem criar alerta. */
export function testarAparelho(id: number): Promise<{ enviado: boolean; erro: string | null }> {
  return postJson(`/push-devices/${id}/teste`, {})
}
