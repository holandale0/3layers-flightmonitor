-- =============================================================================
-- Flight Monitor - schema inicial (etapa E1.1)
--
-- Convencoes:
--   * identificadores em ingles e snake_case
--   * chaves primarias com IDENTITY (padrao SQL, PostgreSQL 10+)
--   * instantes em timestamptz; datas de voo em date (nao tem fuso)
--   * dinheiro em numeric(10,2) - nunca float, para nao perder centavos
-- =============================================================================


-- Mantem updated_at coerente sem depender da aplicacao lembrar de setar.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- monitor : o que o usuario quer vigiar
-- =============================================================================
CREATE TABLE monitor (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label                   varchar(120),
    origin                  varchar(3)  NOT NULL,
    destination             varchar(3)  NOT NULL,

    -- Janela de ida: o monitor varre todos os dias entre estas duas datas.
    departure_window_start  date        NOT NULL,
    departure_window_end    date        NOT NULL,

    -- Janela de volta. Nula nos dois campos = somente ida.
    return_window_start     date,
    return_window_end       date,

    -- Alternativa a janela de volta: permanencia em dias no destino.
    min_stay_days           smallint,
    max_stay_days           smallint,

    max_price               numeric(10,2) NOT NULL,
    currency                varchar(3)    NOT NULL DEFAULT 'BRL',
    max_stops               smallint,
    passengers              smallint      NOT NULL DEFAULT 1,

    active                  boolean       NOT NULL DEFAULT true,
    search_interval_minutes integer       NOT NULL DEFAULT 360,
    last_searched_at        timestamptz,
    next_search_at          timestamptz   NOT NULL DEFAULT now(),

    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT monitor_origin_iata        CHECK (origin ~ '^[A-Z]{3}$'),
    CONSTRAINT monitor_destination_iata   CHECK (destination ~ '^[A-Z]{3}$'),
    CONSTRAINT monitor_rota_distinta      CHECK (origin <> destination),
    CONSTRAINT monitor_currency_iso       CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT monitor_janela_ida_valida  CHECK (departure_window_end >= departure_window_start),
    CONSTRAINT monitor_janela_volta_par   CHECK (
        (return_window_start IS NULL) = (return_window_end IS NULL)
    ),
    CONSTRAINT monitor_janela_volta_valida CHECK (
        return_window_end IS NULL OR return_window_end >= return_window_start
    ),
    CONSTRAINT monitor_permanencia_par    CHECK (
        (min_stay_days IS NULL) = (max_stay_days IS NULL)
    ),
    CONSTRAINT monitor_permanencia_valida CHECK (
        max_stay_days IS NULL OR (min_stay_days >= 1 AND max_stay_days >= min_stay_days)
    ),
    CONSTRAINT monitor_preco_positivo     CHECK (max_price > 0),
    CONSTRAINT monitor_escalas_validas    CHECK (max_stops IS NULL OR max_stops >= 0),
    CONSTRAINT monitor_passageiros_validos CHECK (passengers BETWEEN 1 AND 9),
    CONSTRAINT monitor_intervalo_valido   CHECK (search_interval_minutes >= 5)
);

CREATE TRIGGER monitor_set_updated_at
    BEFORE UPDATE ON monitor
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- O scheduler pergunta "quais monitores estao vencidos?" a cada ciclo.
CREATE INDEX idx_monitor_proxima_busca ON monitor (next_search_at) WHERE active;

COMMENT ON TABLE  monitor IS 'Criterios de busca que o sistema vigia periodicamente';
COMMENT ON COLUMN monitor.next_search_at IS 'Quando o scheduler deve varrer este monitor de novo';


-- =============================================================================
-- recipient : quem recebe os alertas no WhatsApp
-- =============================================================================
CREATE TABLE recipient (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(120) NOT NULL,
    phone_e164  varchar(16)  NOT NULL UNIQUE,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    -- E.164: "+" seguido de 8 a 15 digitos, sem espaco ou pontuacao.
    CONSTRAINT recipient_telefone_e164 CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$')
);

CREATE TRIGGER recipient_set_updated_at
    BEFORE UPDATE ON recipient
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN recipient.phone_e164 IS 'Telefone em formato E.164, ex: +5511999998888';


-- =============================================================================
-- monitor_recipient : quem recebe alerta de qual monitor
-- =============================================================================
CREATE TABLE monitor_recipient (
    monitor_id   bigint NOT NULL REFERENCES monitor (id)   ON DELETE CASCADE,
    recipient_id bigint NOT NULL REFERENCES recipient (id) ON DELETE CASCADE,
    PRIMARY KEY (monitor_id, recipient_id)
);

CREATE INDEX idx_monitor_recipient_destinatario ON monitor_recipient (recipient_id);


-- =============================================================================
-- search_run : uma execucao de varredura, para rastrear falhas de provider
-- =============================================================================
CREATE TABLE search_run (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monitor_id         bigint      REFERENCES monitor (id) ON DELETE SET NULL,
    source             varchar(30) NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'RUNNING',
    started_at         timestamptz NOT NULL DEFAULT now(),
    finished_at        timestamptz,
    observations_count integer     NOT NULL DEFAULT 0,
    error_message      text,

    CONSTRAINT search_run_status_valido CHECK (
        status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')
    ),
    CONSTRAINT search_run_source_valida CHECK (
        source IN ('TRAVELPAYOUTS', 'FAST_FLIGHTS')
    ),
    CONSTRAINT search_run_fim_apos_inicio CHECK (
        finished_at IS NULL OR finished_at >= started_at
    )
);

CREATE INDEX idx_search_run_monitor ON search_run (monitor_id, started_at DESC);

COMMENT ON TABLE search_run IS
    'Historico de execucoes. Permite medir taxa de falha por fonte de preco.';


-- =============================================================================
-- price_observation : cada preco visto. E a tabela mais importante do sistema.
--
-- origin/destination sao DENORMALIZADOS de proposito: a media historica de uma
-- rota pertence a rota, nao ao monitor. Dois monitores GRU->LIS compartilham o
-- mesmo historico, e apagar um monitor nao pode apagar o que ja foi aprendido
-- sobre a rota. Por isso monitor_id e ON DELETE SET NULL.
-- =============================================================================
CREATE TABLE price_observation (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monitor_id       bigint       REFERENCES monitor (id)    ON DELETE SET NULL,
    search_run_id    bigint       REFERENCES search_run (id) ON DELETE SET NULL,

    origin           varchar(3)   NOT NULL,
    destination      varchar(3)   NOT NULL,
    departure_date   date         NOT NULL,
    return_date      date,

    price            numeric(10,2) NOT NULL,
    currency         varchar(3)    NOT NULL DEFAULT 'BRL',

    airline          varchar(80),
    stops            smallint,
    duration_minutes integer,
    -- Horarios de voo sao locais do aeroporto: timestamp sem fuso, de proposito.
    departure_at     timestamp,
    arrival_at       timestamp,

    source           varchar(30)  NOT NULL,
    confirmed        boolean      NOT NULL DEFAULT false,
    observed_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT observation_origin_iata      CHECK (origin ~ '^[A-Z]{3}$'),
    CONSTRAINT observation_destination_iata CHECK (destination ~ '^[A-Z]{3}$'),
    CONSTRAINT observation_currency_iso     CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT observation_preco_positivo   CHECK (price > 0),
    CONSTRAINT observation_escalas_validas  CHECK (stops IS NULL OR stops >= 0),
    CONSTRAINT observation_volta_apos_ida   CHECK (
        return_date IS NULL OR return_date >= departure_date
    ),
    CONSTRAINT observation_source_valida    CHECK (
        source IN ('TRAVELPAYOUTS', 'FAST_FLIGHTS')
    )
);

-- Historico exibido no painel de um monitor.
CREATE INDEX idx_observation_monitor_tempo
    ON price_observation (monitor_id, observed_at DESC);

-- Estatisticas da Fase 2: media e minimo por rota e mes de partida.
CREATE INDEX idx_observation_rota_data
    ON price_observation (origin, destination, departure_date);

-- Anti-spam (E1.10): "qual foi o ultimo preco visto para esta data?"
CREATE INDEX idx_observation_monitor_datas
    ON price_observation (monitor_id, departure_date, return_date, observed_at DESC);

COMMENT ON TABLE  price_observation IS
    'Cada preco observado. Base de todo o historico e da inteligencia da Fase 2.';
COMMENT ON COLUMN price_observation.confirmed IS
    'true quando a camada 2 (fast-flights) confirmou o preco da camada 1';
COMMENT ON COLUMN price_observation.departure_at IS
    'Horario local do aeroporto de origem - por isso sem fuso';


-- =============================================================================
-- alert : uma linha por envio a um destinatario
-- =============================================================================
CREATE TABLE alert (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monitor_id           bigint      REFERENCES monitor (id)           ON DELETE SET NULL,
    price_observation_id bigint      REFERENCES price_observation (id) ON DELETE SET NULL,
    recipient_id         bigint      REFERENCES recipient (id)         ON DELETE SET NULL,

    channel              varchar(20) NOT NULL DEFAULT 'WHATSAPP',
    status               varchar(20) NOT NULL DEFAULT 'PENDING',
    message              text        NOT NULL,
    provider_message_id  varchar(120),
    error_message        text,
    sent_at              timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT alert_channel_valido CHECK (channel IN ('WHATSAPP', 'LOG')),
    CONSTRAINT alert_status_valido  CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- Anti-spam (E1.10): "ja alertei este monitor recentemente?"
CREATE INDEX idx_alert_monitor_tempo ON alert (monitor_id, created_at DESC);
CREATE INDEX idx_alert_pendentes     ON alert (status) WHERE status = 'PENDING';

COMMENT ON TABLE alert IS 'Uma linha por envio a um destinatario, com o resultado da entrega';
