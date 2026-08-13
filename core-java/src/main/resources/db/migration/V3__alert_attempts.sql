-- =============================================================================
-- Contador de tentativas de entrega.
--
-- Sem ele, retentativa vira armadilha: uma falha transitoria (rede instavel,
-- HTTP 500 da Meta) deve ser retentada, mas sem contador nao ha como parar —
-- um canal permanentemente quebrado ficaria em laco infinito, e um numero de
-- telefone invalido seria retentado para sempre.
--
-- Com o contador, a regra fica explicita:
--   falha transitoria  -> attempts++ e continua PENDING, ate o limite
--   falha permanente   -> FAILED de imediato, sem gastar tentativa
--   limite atingido    -> FAILED
-- =============================================================================

ALTER TABLE alert ADD COLUMN attempts integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN alert.attempts IS
    'Quantas vezes tentamos entregar. Limita a retentativa de falha transitoria.';

-- O despachante busca alertas pendentes na ordem em que foram criados.
-- Indice parcial: so as linhas PENDING interessam, e elas sao poucas.
DROP INDEX IF EXISTS idx_alert_pendentes;
CREATE INDEX idx_alert_pendentes ON alert (created_at, id) WHERE status = 'PENDING';
