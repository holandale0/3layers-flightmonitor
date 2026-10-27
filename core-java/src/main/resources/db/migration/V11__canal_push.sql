-- =============================================================================
-- V11 : notificacao no aplicativo, por push (D-113)
--
-- Duas mudancas, ambas ADITIVAS: nenhuma linha existente deixa de ser valida.
--
--   1. `alert.channel` passa a aceitar 'PUSH'
--   2. `push_device`: os aparelhos que recebem push, cada um de um destinatario
--
-- O aparelho e um CONTATO do destinatario, como o telefone e o e-mail — so que
-- uma pessoa pode ter varios (celular e tablet), e cada um nasce quando o
-- aplicativo e instalado, e nao quando alguem digita algo num formulario.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. O canal novo
--
-- Mesmo procedimento da V7: PostgreSQL nao tem ALTER CONSTRAINT para mudar a
-- expressao de um CHECK, e como a lista so CRESCE, nada gravado fica invalido.
-- -----------------------------------------------------------------------------
ALTER TABLE alert DROP CONSTRAINT alert_channel_valido;

ALTER TABLE alert ADD CONSTRAINT alert_channel_valido
    CHECK (channel IN ('WHATSAPP', 'LOG', 'EMAIL', 'PUSH'));


-- -----------------------------------------------------------------------------
-- 2. Os aparelhos
--
-- `ON DELETE CASCADE`, ao contrario do alerta: o alerta e HISTORIA e sobrevive
-- ao destinatario apagado; o aparelho e so um meio de alcancar alguem que
-- deixou de existir.
--
-- O token e emitido pelo Firebase e pode mudar (reinstalacao, limpeza de dados
-- do app). Unico, porque o mesmo aparelho registrado duas vezes receberia o
-- alerta em dobro — e o registro repetido nao e erro, e o app se reapresentando.
--
-- `active` desliga sem apagar: quando o Firebase responde que o token nao vale
-- mais, o aparelho fica registrado com o motivo em `last_error`, e quem abrir a
-- tela entende por que o celular parou de receber.
-- -----------------------------------------------------------------------------
CREATE TABLE push_device (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id  bigint       NOT NULL REFERENCES recipient (id) ON DELETE CASCADE,
    token         text         NOT NULL,
    name          varchar(120) NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    last_error    text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    -- Um token FCM tem ~160 caracteres. O limite nao e o formato, que o
    -- Firebase nao documenta como estavel; e impedir lixo de megabytes.
    CONSTRAINT push_device_token_plausivel CHECK (length(token) BETWEEN 20 AND 4096),
    CONSTRAINT push_device_nome_preenchido CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX idx_push_device_token ON push_device (token);
CREATE INDEX idx_push_device_recipient ON push_device (recipient_id) WHERE active;

CREATE TRIGGER push_device_set_updated_at
    BEFORE UPDATE ON push_device
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE push_device IS
    'Aparelhos com o aplicativo instalado, que recebem alertas pelo canal PUSH';
COMMENT ON COLUMN push_device.last_error IS
    'Por que o aparelho foi desativado, quando foi o Firebase quem recusou o token';
