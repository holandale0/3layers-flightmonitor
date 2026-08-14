-- Testa as regras do schema. Roda em transacao e faz ROLLBACK no fim.
\set ON_ERROR_STOP on
BEGIN;

CREATE OR REPLACE FUNCTION deve_rejeitar(comando text, rotulo text) RETURNS void AS $$
BEGIN
    BEGIN
        EXECUTE comando;
        RAISE EXCEPTION 'FALHOU -> "%" foi ACEITO mas deveria ser rejeitado', rotulo;
    EXCEPTION
        WHEN check_violation OR unique_violation OR foreign_key_violation
             OR not_null_violation OR string_data_right_truncation THEN
            RAISE NOTICE 'ok  rejeitou: %', rotulo;
    END;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------- monitor
INSERT INTO monitor (label, origin, destination, departure_window_start,
                     departure_window_end, min_stay_days, max_stay_days,
                     max_price, max_stops)
VALUES ('Lisboa 2027', 'GRU', 'LIS', '2027-03-10', '2027-03-20', 10, 15, 3200.00, 1);
\echo 'ok  aceitou monitor valido'

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price)
    VALUES ('gru','LIS','2027-03-10','2027-03-20',3200)$$, 'IATA em minusculas');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price)
    VALUES ('GRU','GRU','2027-03-10','2027-03-20',3200)$$, 'origem igual ao destino');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price)
    VALUES ('GRU','LIS','2027-03-20','2027-03-10',3200)$$, 'janela de ida invertida');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price, return_window_start)
    VALUES ('GRU','LIS','2027-03-10','2027-03-20',3200,'2027-04-01')$$, 'janela de volta pela metade');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price)
    VALUES ('GRU','LIS','2027-03-10','2027-03-20',0)$$, 'preco-teto zerado');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price, passengers)
    VALUES ('GRU','LIS','2027-03-10','2027-03-20',3200,0)$$, 'zero passageiros');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price, min_stay_days, max_stay_days)
    VALUES ('GRU','LIS','2027-03-10','2027-03-20',3200,15,10)$$, 'permanencia minima maior que a maxima');

SELECT deve_rejeitar($$INSERT INTO monitor (origin, destination, departure_window_start, departure_window_end, max_price, search_interval_minutes)
    VALUES ('GRU','LIS','2027-03-10','2027-03-20',3200,9)$$, 'intervalo de busca menor que 10 min');


-- -------------------------------------------------------------- recipient
INSERT INTO recipient (name, phone_e164) VALUES ('Leonardo', '+5511999998888');
\echo 'ok  aceitou destinatario valido'

SELECT deve_rejeitar($$INSERT INTO recipient (name, phone_e164) VALUES ('Sem mais','+55 11 99999-8888')$$,
    'telefone com espacos e hifen');
SELECT deve_rejeitar($$INSERT INTO recipient (name, phone_e164) VALUES ('Sem mais','5511999998888')$$,
    'telefone sem o + do E.164');
SELECT deve_rejeitar($$INSERT INTO recipient (name, phone_e164) VALUES ('Duplicado','+5511999998888')$$,
    'telefone duplicado');


-- ----------------------------------------------------- vinculo e cascata
INSERT INTO monitor_recipient (monitor_id, recipient_id)
SELECT m.id, r.id FROM monitor m, recipient r;
\echo 'ok  vinculou monitor ao destinatario'

SELECT deve_rejeitar($$INSERT INTO monitor_recipient (monitor_id, recipient_id) VALUES (999999, 1)$$,
    'vinculo apontando para monitor inexistente');


-- ------------------------------------------------------------ trigger
-- O que importa nao e "o tempo passou" (now() e constante dentro de uma
-- transacao), e sim que o trigger IGNORA o valor enviado pela aplicacao e
-- impoe o seu proprio. Assim updated_at nunca pode ser forjado.
DO $$
DECLARE resultado timestamptz;
BEGIN
    UPDATE monitor SET max_price = 3000, updated_at = '2000-01-01'::timestamptz;
    SELECT updated_at INTO resultado FROM monitor LIMIT 1;
    IF resultado > '2020-01-01'::timestamptz THEN
        RAISE NOTICE 'ok  trigger sobrepos o updated_at enviado pela aplicacao';
    ELSE
        RAISE EXCEPTION 'FALHOU -> trigger nao sobrepos updated_at (ficou %)', resultado;
    END IF;
END $$;


-- -------------------------------------------------- search e observacao
INSERT INTO search_run (monitor_id, source)
SELECT id, 'TRAVELPAYOUTS' FROM monitor LIMIT 1;
\echo 'ok  aceitou search_run valido'

SELECT deve_rejeitar($$INSERT INTO search_run (source) VALUES ('KAYAK')$$, 'fonte de preco desconhecida');
SELECT deve_rejeitar($$INSERT INTO search_run (source, status) VALUES ('TRAVELPAYOUTS','TALVEZ')$$, 'status de execucao invalido');

INSERT INTO price_observation (monitor_id, search_run_id, origin, destination,
                               departure_date, return_date, price, airline, stops, source)
SELECT m.id, s.id, 'GRU', 'LIS', '2027-03-12', '2027-03-27', 2980.00, 'LATAM', 0, 'TRAVELPAYOUTS'
FROM monitor m, search_run s;
\echo 'ok  aceitou observacao de preco valida'

SELECT deve_rejeitar($$INSERT INTO price_observation (origin,destination,departure_date,price,source)
    VALUES ('GRU','LIS','2027-03-12',-10,'TRAVELPAYOUTS')$$, 'preco negativo');
SELECT deve_rejeitar($$INSERT INTO price_observation (origin,destination,departure_date,return_date,price,source)
    VALUES ('GRU','LIS','2027-03-12','2027-03-01',2980,'TRAVELPAYOUTS')$$, 'volta antes da ida');


-- ------------------------------------------------------------- alerta
INSERT INTO alert (monitor_id, price_observation_id, recipient_id, message)
SELECT m.id, o.id, r.id, 'GRU->LIS por R$ 2.980'
FROM monitor m, price_observation o, recipient r;
\echo 'ok  aceitou alerta valido'

SELECT deve_rejeitar($$INSERT INTO alert (message, channel) VALUES ('oi','TELEGRAM')$$, 'canal nao suportado');


-- =====================================================================
-- O TESTE MAIS IMPORTANTE: apagar um monitor NAO pode apagar o historico
-- de precos daquela rota. O que o sistema aprendeu sobre GRU->LIS
-- pertence a rota, nao ao monitor.
-- =====================================================================
DO $$
DECLARE antes int; depois int; orfas int;
BEGIN
    SELECT count(*) INTO antes FROM price_observation;
    DELETE FROM monitor;
    SELECT count(*) INTO depois FROM price_observation;
    SELECT count(*) INTO orfas FROM price_observation WHERE monitor_id IS NULL;

    IF depois = antes AND orfas = depois THEN
        RAISE NOTICE 'ok  historico sobreviveu a exclusao do monitor (% observacoes, monitor_id nulo)', depois;
    ELSE
        RAISE EXCEPTION 'FALHOU -> historico perdido: antes=% depois=%', antes, depois;
    END IF;

    IF (SELECT count(*) FROM monitor_recipient) = 0 THEN
        RAISE NOTICE 'ok  vinculo monitor_recipient foi removido em cascata';
    ELSE
        RAISE EXCEPTION 'FALHOU -> vinculo orfao em monitor_recipient';
    END IF;
END $$;

ROLLBACK;
