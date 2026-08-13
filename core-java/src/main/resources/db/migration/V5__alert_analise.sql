-- =============================================================================
-- O que o alerta sabia quando decidiu — etapa E2.4.
--
-- A mensagem enriquecida precisa da nota e da comparacao historica no momento
-- da ENTREGA, que acontece fora de transacao e com a entidade desanexada (a
-- licao do BUG-006). Recalcular estatistica dentro do adaptador do WhatsApp
-- seria camada errada e momento errado.
--
-- Ha um motivo melhor do que conveniencia. O alerta deve registrar o que ele
-- sabia QUANDO DECIDIU. Recalcular na entrega — ou pior, ao ler o historico
-- meses depois — produziria um numero diferente do que motivou o alerta, e o
-- registro passaria a mentir sobre o proprio passado.
--
-- Todas as colunas sao NULL: alerta de rota sem historico suficiente nao tem
-- analise, e NULL diz exatamente isso. Zero diria "nota zero", que e outra
-- coisa — a mesma distincao que governa a E2.3.
-- =============================================================================

ALTER TABLE alert ADD COLUMN flight_score    smallint;
ALTER TABLE alert ADD COLUMN anomaly_grade   varchar(20);
ALTER TABLE alert ADD COLUMN anomaly_drop_pct numeric(6,2);

COMMENT ON COLUMN alert.flight_score IS
    'Nota 0-100 do voo na hora do alerta. NULL = nao havia base para pontuar.';
COMMENT ON COLUMN alert.anomaly_grade IS
    'Grau de anomalia do preco na hora do alerta. NULL = nao havia base para julgar.';
COMMENT ON COLUMN alert.anomaly_drop_pct IS
    'Quanto o preco estava abaixo da mediana da rota, em pontos percentuais.';

-- A escala e 0 a 100. Um valor fora disso indica erro de calculo, e e melhor
-- descobrir na hora de gravar do que ao ler um historico ja corrompido.
ALTER TABLE alert ADD CONSTRAINT alert_score_valido
    CHECK (flight_score IS NULL OR flight_score BETWEEN 0 AND 100);

-- Os nomes batem com o enum GrauDeAnomalia. SEM_DADOS e NORMAL nao sao
-- gravados: a coluna existe para o que vira mensagem, e esses dois nao viram.
ALTER TABLE alert ADD CONSTRAINT alert_anomaly_grade_valido
    CHECK (anomaly_grade IS NULL OR anomaly_grade IN ('BOM', 'EXCELENTE', 'RECORDE'));
