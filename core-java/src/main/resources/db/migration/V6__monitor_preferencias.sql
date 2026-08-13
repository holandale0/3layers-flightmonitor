-- =============================================================================
-- Preferencias por monitor — etapa E2.6.
--
-- Ate aqui o sistema tratava toda viagem igual. Quem vai a trabalho quer voo
-- direto de manha e paga mais por isso; quem vai passear aceita escala e
-- madrugada para economizar. A mesma nota nao serve para os dois.
--
-- O que ENTRA aqui, e por que:
--
--   companhias evitadas   -> temos o campo `airline` nas duas camadas
--   preferencia por direto -> temos `stops` nas duas camadas
--   pesos do score        -> sao nossos, nao dependem de fonte externa
--
-- O que NAO entra, e por que — ver D-075:
--
--   bagagem               -> NENHUMA das fontes devolve franquia de bagagem.
--                            Guardar a preferencia sem ter o dado criaria um
--                            campo que parece funcionar e nao filtra nada
--   aeroporto alternativo -> exige a varredura abrir em varios pares de rota,
--                            e decidir a qual rota pertence o historico
--                            resultante. Mexe na D-016 e merece etapa propria
-- =============================================================================

-- Preferencia, e nao exigencia. `max_stops` ja existe e e limite RIGIDO,
-- enviado a fonte. Isto aqui e mais suave: o voo com escala continua valendo,
-- so vale menos na nota.
ALTER TABLE monitor ADD COLUMN prefere_voo_direto boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN monitor.prefere_voo_direto IS
    'Penaliza escalas com mais forca na nota. Diferente de max_stops, que exclui.';

-- Pesos do Flight Score. NULL = usa o peso global de flightmonitor.score.*
-- Nulo e nao zero: zero seria "este aspecto nao importa", que e uma escolha
-- legitima e diferente de "nao escolhi nada".
ALTER TABLE monitor ADD COLUMN peso_preco    smallint;
ALTER TABLE monitor ADD COLUMN peso_escalas  smallint;
ALTER TABLE monitor ADD COLUMN peso_duracao  smallint;
ALTER TABLE monitor ADD COLUMN peso_horario  smallint;

ALTER TABLE monitor ADD CONSTRAINT monitor_pesos_validos CHECK (
    (peso_preco   IS NULL OR peso_preco   BETWEEN 0 AND 100) AND
    (peso_escalas IS NULL OR peso_escalas BETWEEN 0 AND 100) AND
    (peso_duracao IS NULL OR peso_duracao BETWEEN 0 AND 100) AND
    (peso_horario IS NULL OR peso_horario BETWEEN 0 AND 100)
);

-- Todos os pesos zerados deixariam o score sem nada para somar. Melhor recusar
-- na gravacao do que devolver nota nula sem explicacao depois.
ALTER TABLE monitor ADD CONSTRAINT monitor_pesos_nao_todos_zero CHECK (
    coalesce(peso_preco, 1) + coalesce(peso_escalas, 1)
    + coalesce(peso_duracao, 1) + coalesce(peso_horario, 1) > 0
);

-- =============================================================================
-- Companhias evitadas.
--
-- Tabela e nao coluna com lista separada por virgula: virgula dentro do nome de
-- uma companhia quebraria a lista em silencio, e a camada 2 devolve nomes por
-- extenso ("Tap Air Portugal"). Tabela tambem torna a consulta possivel.
-- =============================================================================

CREATE TABLE monitor_avoided_airline (
    monitor_id bigint      NOT NULL REFERENCES monitor (id) ON DELETE CASCADE,
    airline    varchar(60) NOT NULL,

    PRIMARY KEY (monitor_id, airline),

    -- Guardado ja normalizado: em maiuscula e sem espaco nas pontas. A
    -- comparacao com o que a fonte devolve acontece dezenas de vezes por
    -- varredura, e normalizar dos dois lados a cada comparacao seria trabalho
    -- repetido para sempre.
    CONSTRAINT avoided_airline_normalizada CHECK (airline = upper(btrim(airline))),
    CONSTRAINT avoided_airline_nao_vazia   CHECK (btrim(airline) <> '')
);

COMMENT ON TABLE monitor_avoided_airline IS
    'Companhias que este monitor nao quer. Codigo IATA de duas letras casa tambem com o nome por extenso.';
