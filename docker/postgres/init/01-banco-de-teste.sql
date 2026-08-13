-- Cria o banco que a suite de testes usa — etapa E4.2, fechando o RISCO-008.
--
-- # Por que existe
--
-- Ate aqui os testes de integracao usavam o MESMO banco do desenvolvimento
-- (D-020) e limpavam as tabelas a cada metodo. Rodar `mvn test` apagava
-- monitores, destinatarios, observacoes e alertas cadastrados a mao.
--
-- Custou dados duas vezes em dois dias. Na segunda, levou junto o unico
-- registro do numero de destino usado no teste da E1.12 — nao estava em
-- documento nenhum, e a Graph API nao devolve historico de conversas. A etapa
-- parou ate o usuario informa-lo de novo.
--
-- "Banco descartavel" valia enquanto o dado fosse recriavel por quem apagou. Um
-- dado que precisou de OUTRA PESSOA para voltar nao era descartavel.
--
-- # O que NAO muda
--
-- Continua sendo PostgreSQL de verdade, no mesmo container, com o mesmo Flyway
-- aplicando as mesmas migrations. O valor dos testes de constraint e trigger
-- (D-020) esta inteiro — o que muda e so em qual banco eles fazem a bagunca.
--
-- # Atencao ao volume existente
--
-- Scripts em /docker-entrypoint-initdb.d/ so rodam quando o volume e criado do
-- zero. Em instalacao que ja existia, este banco foi criado a mao uma vez:
--
--   docker compose exec postgres psql -U flightmon -d flightmon \
--       -c "CREATE DATABASE flightmon_test OWNER flightmon"

CREATE DATABASE flightmon_test OWNER flightmon;

COMMENT ON DATABASE flightmon_test IS
    'Banco da suite de testes (E4.2). E apagado e recriado a vontade; o de desenvolvimento nao.';
