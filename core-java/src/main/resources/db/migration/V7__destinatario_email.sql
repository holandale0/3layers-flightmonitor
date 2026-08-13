-- =============================================================================
-- V7 : canal de e-mail (etapa E4.6)
--
-- Duas mudancas, ambas ADITIVAS: nenhuma linha existente deixa de ser valida.
--
--   1. `alert.channel` passa a aceitar 'EMAIL'
--   2. `recipient` ganha `email`, e `phone_e164` deixa de ser obrigatorio
--
-- O porque desta etapa existir esta em D-097: o WhatsApp se mostrou bloqueavel
-- por terceiro — o template ficou dias em analise na Meta, e nesse periodo o
-- sistema fazia todo o trabalho e nao conseguia avisar ninguem.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. O canal novo
--
-- Recriar o CHECK e a unica forma: PostgreSQL nao tem "ALTER CONSTRAINT" para
-- mudar a expressao de um CHECK. Como a lista so CRESCE, nenhuma linha gravada
-- vira invalida no intervalo.
-- -----------------------------------------------------------------------------
ALTER TABLE alert DROP CONSTRAINT alert_channel_valido;

ALTER TABLE alert ADD CONSTRAINT alert_channel_valido
    CHECK (channel IN ('WHATSAPP', 'LOG', 'EMAIL'));


-- -----------------------------------------------------------------------------
-- 2. O endereco de e-mail do destinatario
--
-- 254 caracteres e o limite real de um endereco de e-mail (RFC 5321): 64 para a
-- parte local, 1 para a arroba, 189 para o dominio. Nao e numero redondo
-- escolhido no olho.
-- -----------------------------------------------------------------------------
ALTER TABLE recipient ADD COLUMN email varchar(254);

COMMENT ON COLUMN recipient.email IS
    'Endereco para o canal EMAIL. Nulo quando o destinatario so recebe por WhatsApp';


-- O telefone deixa de ser obrigatorio: um destinatario que so recebe por e-mail
-- nao tem telefone, e exigir um numero falso para satisfazer a coluna produziria
-- dado mentiroso — e um numero falso que um dia recebe mensagem de verdade.
ALTER TABLE recipient ALTER COLUMN phone_e164 DROP NOT NULL;


-- Mas nao pode sobrar destinatario que nao possa ser alcancado por nada. O CHECK
-- guarda exatamente esse estado sem sentido: uma pessoa cadastrada para receber
-- alertas, sem nenhuma forma de receber alerta.
ALTER TABLE recipient ADD CONSTRAINT recipient_tem_algum_contato
    CHECK (phone_e164 IS NOT NULL OR email IS NOT NULL);


-- Formato validado no banco, pelo mesmo motivo do `recipient_telefone_e164`: a
-- aplicacao tambem valida, mas o banco e a ultima linha — ele protege contra
-- carga manual, script de migracao e bug nosso.
--
-- A expressao e deliberadamente FROUXA. Validar e-mail por regex estrita e uma
-- armadilha conhecida: a gramatica real da RFC 5322 aceita coisas que quase toda
-- regex rejeita, e o erro tipico e recusar um endereco valido de alguem que
-- entao nao consegue se cadastrar. Aqui basta impedir o obviamente errado —
-- espaco, arroba faltando, dominio sem ponto.
ALTER TABLE recipient ADD CONSTRAINT recipient_email_plausivel
    CHECK (email IS NULL OR email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$');


-- Unico como o telefone, e pela mesma razao: dois cadastros com o mesmo endereco
-- fariam a mesma caixa receber o alerta duas vezes.
--
-- Indice parcial em vez de UNIQUE na coluna: no PostgreSQL varios NULLs nao
-- colidem num indice unico, entao o resultado seria o mesmo — mas o `WHERE`
-- deixa explicito para quem le que a ausencia de e-mail nao e um valor que
-- disputa unicidade.
CREATE UNIQUE INDEX idx_recipient_email ON recipient (email) WHERE email IS NOT NULL;
