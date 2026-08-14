-- =============================================================================
-- V9 : intervalo minimo de varredura sobe de 5 para 10 minutos
--
-- # Por que
--
-- Cinco minutos era generoso demais para o que as fontes suportam. As duas sao
-- gratuitas e nao contratadas: a Travelpayouts publica limite de 300 req/min, e
-- a camada 2 nao publica limite nenhum — o que nao significa que nao exista,
-- significa que voce descobre qual e quando for bloqueado (RISCO-004).
--
-- E o ganho de varrer de 5 em 5 minutos e proximo de zero: preco de passagem
-- muda em minutos, mas a camada 1 devolve dado CACHEADO, com horas de atraso.
-- Consultar mais rapido que o cache atualiza gasta cota para reler a mesma
-- resposta.
--
-- # A ordem importa
--
-- O UPDATE vem ANTES do CHECK. Na ordem inversa, a migration falharia em
-- qualquer instalacao que ja tivesse um monitor abaixo de 10 — e falharia no
-- meio do deploy, com o schema pela metade.
-- =============================================================================

-- Sobe quem estava abaixo do novo minimo. E mudanca de dado do usuario, entao
-- fica registrada: preferivel a recusar a subir, ou a manter monitor que o
-- CHECK novo proibiria.
UPDATE monitor
   SET search_interval_minutes = 10
 WHERE search_interval_minutes < 10;


-- Recriar e a unica forma: PostgreSQL nao tem ALTER CONSTRAINT para mudar a
-- expressao de um CHECK.
ALTER TABLE monitor DROP CONSTRAINT monitor_intervalo_valido;

ALTER TABLE monitor ADD CONSTRAINT monitor_intervalo_valido
    CHECK (search_interval_minutes >= 10);

COMMENT ON COLUMN monitor.search_interval_minutes IS
    'Minutos entre varreduras deste monitor. Minimo de 10: as fontes sao gratuitas e nao contratadas, e a camada 1 devolve dado cacheado (V9)';
