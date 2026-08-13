-- =============================================================================
-- Entrega confirmada, e nao apenas aceita.
--
-- O BUG-007 custou horas porque o sistema dizia SENT para quatro mensagens que
-- NUNCA chegaram. A Graph API devolveu `wamid` e `message_status: accepted`
-- para todas elas — e a falha real (codigo 130497, "conta restrita de enviar
-- mensagens para usuarios neste pais") sO existia no webhook, que ninguem
-- estava lendo.
--
-- A licao, registrada na D-053: `wamid` e um numero de protocolo, nao um
-- comprovante de entrega. Tratar um como o outro faz o historico mentir, e faz
-- mentir exatamente quando ha um problema — o pior momento possivel.
--
-- Esta migracao separa os dois conceitos:
--
--   ACCEPTED  a Meta recebeu e devolveu wamid. NAO sabemos se chegou
--   SENT      o webhook confirmou a entrega no aparelho
--   FAILED    o webhook informou que a entrega falhou, com o motivo
--
-- Canais de confirmacao SINCRONA (o LOG) continuam indo direto para SENT: para
-- eles nao existe webhook, e a entrega e certa no momento em que acontece.
-- =============================================================================

ALTER TABLE alert ADD COLUMN delivered_at timestamptz;
ALTER TABLE alert ADD COLUMN read_at timestamptz;

COMMENT ON COLUMN alert.delivered_at IS
    'Quando a Meta confirmou entrega no aparelho, via webhook. Nulo = nao confirmada.';
COMMENT ON COLUMN alert.read_at IS
    'Quando o destinatario abriu a mensagem. Depende de confirmacao de leitura ligada.';

-- O status ACCEPTED nao existia. Sem ampliar o CHECK, o adaptador do WhatsApp
-- passaria a violar constraint no primeiro envio.
ALTER TABLE alert DROP CONSTRAINT alert_status_valido;
ALTER TABLE alert ADD CONSTRAINT alert_status_valido
    CHECK (status IN ('PENDING', 'ACCEPTED', 'SENT', 'FAILED'));

-- Coerencia de datas, COM TOLERANCIA de um minuto.
--
-- A tolerancia nao e preguica. O timestamp do webhook vem em segundos inteiros,
-- enquanto o nosso `sent_at` tem microssegundos: uma mensagem aceita as
-- 10:00:00.500 e entregue no mesmo segundo chega como 10:00:00, que e ANTERIOR
-- ao envio. Somando a isso a diferenca de relogio entre os servidores da Meta e
-- o nosso, uma comparacao exata rejeitaria entregas legitimas — e o alerta
-- ficaria preso em ACCEPTED por causa de meio segundo.
--
-- Um minuto ainda barra o que e de fato impossivel: entrega horas antes do
-- envio, que indicaria wamid trocado ou payload corrompido.
ALTER TABLE alert ADD CONSTRAINT alert_entrega_apos_envio
    CHECK (delivered_at IS NULL OR sent_at IS NULL
           OR delivered_at >= sent_at - interval '1 minute');
ALTER TABLE alert ADD CONSTRAINT alert_leitura_apos_entrega
    CHECK (read_at IS NULL OR delivered_at IS NULL
           OR read_at >= delivered_at - interval '1 minute');

-- O webhook chega com o `wamid` e mais nada: e por ele que o alerta e
-- encontrado. Sem indice, cada notificacao da Meta viraria varredura da tabela.
-- Parcial porque so linhas ja despachadas tem o identificador.
CREATE UNIQUE INDEX idx_alert_provider_message_id
    ON alert (provider_message_id)
    WHERE provider_message_id IS NOT NULL;

-- O indice de pendentes cobria apenas PENDING, o que continua correto: ACCEPTED
-- nao pode voltar para a fila de despacho — reenviar significaria mensagem
-- repetida no WhatsApp de quem recebe.
