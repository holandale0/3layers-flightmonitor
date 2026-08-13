-- =============================================================================
-- Corrige os timestamps de EVENTO para usar o relogio real.
--
-- Problema: now() no PostgreSQL devolve o horario de INICIO DA TRANSACAO, nao
-- o horario atual. Todas as linhas gravadas numa mesma transacao recebiam o
-- mesmo instante.
--
-- Isso quebrava a pergunta central do anti-spam (etapa E1.10): "qual foi o
-- ultimo preco visto para esta data?". Numa mesma varredura, a camada 1
-- (Travelpayouts) e a camada 2 (fast-flights) gravam observacoes para a mesma
-- data dentro da mesma transacao — e com observed_at identico o ORDER BY
-- ficava indefinido. O mesmo valia para alertas enviados a varios
-- destinatarios de uma vez.
--
-- Distincao adotada:
--   * timestamp de EVENTO  (algo aconteceu)      -> clock_timestamp()
--   * timestamp de AUDITORIA (quem tocou a linha) -> now()
--
-- Por isso monitor/recipient.created_at e updated_at continuam com now():
-- ali o instante logico da transacao e a semantica correta.
-- =============================================================================

ALTER TABLE price_observation ALTER COLUMN observed_at SET DEFAULT clock_timestamp();
ALTER TABLE alert             ALTER COLUMN created_at  SET DEFAULT clock_timestamp();
ALTER TABLE search_run        ALTER COLUMN started_at  SET DEFAULT clock_timestamp();

COMMENT ON COLUMN price_observation.observed_at IS
    'Instante real da observacao (clock_timestamp), nao o inicio da transacao';
COMMENT ON COLUMN alert.created_at IS
    'Instante real da criacao do alerta (clock_timestamp)';
