-- =============================================================================
-- V8 : configuracao NAO-SECRETA do WhatsApp no banco (etapa E4.7)
--
-- # Por que
--
-- Ate aqui, apontar o sistema para outro numero ou outro template exigia editar
-- o `.env` e REINICIAR. Para quem clona o projeto, isso significa mexer em
-- arquivo antes de conseguir usar; e para quem ja usa, significa derrubar o
-- servico para trocar um nome de template.
--
-- # O que NAO entra aqui, e o motivo
--
-- Token de acesso, app secret e verify token continuam no `.env`. Coluna de
-- texto em banco vai parar em `pg_dump`, em backup e em qualquer log que
-- serialize a entidade — e guardar segredo aqui exigiria cifra, cuja chave
-- viria... do ambiente. Trocariamos cinco segredos no `.env` por um, e em troca
-- criariamos uma tela que grava credencial, que por sua vez tornaria
-- autenticacao obrigatoria (o projeto nao tem login, por decisao de escopo).
--
-- O que esta aqui e o que identifica *qual* conta e *qual* template usar. Nada
-- disto autentica nada sozinho.
--
-- # Uma linha, e so uma
--
-- O sistema e pessoal e fala por um numero so. Varias linhas criariam a
-- pergunta "qual vale?", que nao tem resposta boa. O CHECK garante isso melhor
-- do que convencao.
-- =============================================================================

CREATE TABLE whatsapp_config (
    id                bigint      PRIMARY KEY DEFAULT 1,

    -- Identificador que a Meta da ao numero REMETENTE. Nao e um telefone: quem
    -- confunde os dois recebe 132000 e demora a entender por que.
    phone_number_id   varchar(40),

    -- Conta do WhatsApp Business dona do template. Existir a WABA certa importa:
    -- o BUG-009 foi um template aprovado na conta ERRADA, e o sintoma era
    -- "template nao encontrado" com o template visivel no painel.
    waba_id           varchar(40),

    template_name     varchar(120) NOT NULL DEFAULT 'alerta_preco_voo',
    template_language varchar(10)  NOT NULL DEFAULT 'pt_BR',

    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT whatsapp_config_linha_unica CHECK (id = 1),

    -- Nome de template da Meta: minusculas, digitos e sublinhado. Recusar aqui
    -- evita descobrir o erro so no primeiro alerta de verdade — que e quando
    -- menos se quer descobrir.
    CONSTRAINT whatsapp_config_template_valido
        CHECK (template_name ~ '^[a-z0-9_]+$'),

    -- Codigo de idioma da Meta usa sublinhado (pt_BR), e nao hifen (pt-BR).
    -- Errar isso devolve 132001, o mesmo erro de "template nao existe".
    CONSTRAINT whatsapp_config_idioma_valido
        CHECK (template_language ~ '^[a-z]{2}(_[A-Z]{2})?$')
);

COMMENT ON TABLE whatsapp_config IS
    'Configuracao nao-secreta do canal WhatsApp (E4.7). Segredos ficam no ambiente.';

COMMENT ON COLUMN whatsapp_config.phone_number_id IS
    'Identificador Meta do numero remetente. NAO e um telefone.';

COMMENT ON COLUMN whatsapp_config.waba_id IS
    'WhatsApp Business Account dona do template. Ver BUG-009.';


-- Gatilho de updated_at, igual ao das outras tabelas (V2).
CREATE TRIGGER whatsapp_config_updated_at
    BEFORE UPDATE ON whatsapp_config
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


-- Nenhuma linha e inserida aqui, DE PROPOSITO.
--
-- Tabela vazia significa "ninguem configurou pela tela", e nesse caso o valor do
-- `.env` continua valendo. E o que mantem funcionando, sem tocar em nada, toda
-- instalacao que ja existia antes desta etapa — inclusive a de quem escreveu.
