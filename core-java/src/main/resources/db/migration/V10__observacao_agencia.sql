-- =============================================================================
-- V10 : a agencia que vende, separada da companhia que voa
--
-- # Por que duas colunas, e nao uma
--
-- A fonte de so ida (`v2/prices/latest`) devolve `gate` — Kiwi.com, Mytrip.com —
-- que e quem VENDE, e nao quem opera o voo. Esse endpoint nao informa a
-- companhia; o calendario de ida e volta informa a companhia e nao informa a
-- agencia. Cada um sabe uma metade.
--
-- Guardar as duas coisas na coluna `airline` seria pratico e errado, e o erro
-- teria consequencia alem da tela: `Preferencias.companhiaEvitada()` compara
-- essa coluna com a lista de companhias que o monitor evita. Com "Kiwi.com"
-- ali, quem pediu "evitar GOL" passaria a comparar GOL com Kiwi.com — e a
-- preferencia pararia de funcionar **em silencio**, que e a pior forma.
--
-- # Na tela elas aparecem juntas, e isso e proposital
--
-- Quem le a tabela quer saber "onde eu compro isso?". Uma coluna so, "Companhia
-- / Agencia", com o que houver — a separacao existe para o CODIGO nao se
-- confundir, e nao para obrigar o leitor a olhar dois lugares.
-- =============================================================================

ALTER TABLE price_observation ADD COLUMN agency varchar(80);

COMMENT ON COLUMN price_observation.agency IS
    'Quem VENDE a passagem (Kiwi.com, Mytrip.com). Diferente de airline, que e quem opera o voo (V10)';

COMMENT ON COLUMN price_observation.airline IS
    'Quem OPERA o voo. Comparada com as companhias evitadas do monitor — nunca receber agencia aqui (V10)';
