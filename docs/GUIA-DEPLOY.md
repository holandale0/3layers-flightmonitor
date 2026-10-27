# Guia — subir em produção

> Servidores caseiros: banco no `lab`, aplicações no `prod`. Decisões em [D-112](DECISOES.md),
> que substitui o túnel Cloudflare da [D-111](DECISOES.md). Infraestrutura em
> `c:\Projects\Infrastructure\personal-private-server` (leia o `CLAUDE.md` de lá antes de mexer).

## O desenho

```
Internet ──HTTPS──▶ Tailscale Funnel :10000 ──▶ painel (nginx, 127.0.0.1:8090) ──▶ core ──▶ worker
                                                     │                              │
                                              senha básica,                   malha Tailscale
                                            exceto no webhook                         │
                                                                     Postgres do lab (100.69.198.43:5432)
```

| Peça | Onde | Detalhe |
|---|---|---|
| core, worker, painel | `prod`, `/srv/apps/flightmonitor` | `docker-compose.yml` + `docker-compose.prod.yml` |
| Banco | `lab`, Postgres 18 compartilhado | banco e usuário `flightmon`; URL em `lab:/srv/datasources/postgres/credenciais/flightmon.url` |
| Segredos | `prod:/srv/apps/flightmonitor/.env` (600) e `segredos/` | nunca no repositório |
| Endereço | `https://prod.tail8feb71.ts.net:10000` | 443 é do `hello`, 8443 do tour-guide |

RabbitMQ e o Postgres do compose base **não sobem** no `prod`: o transporte é REST.

---

## Publicar uma versão nova

A partir da raiz do repositório, neste PC:

```bash
tar czf - --exclude=.git --exclude=node_modules --exclude=target --exclude=dist --exclude=.env \
  --exclude=.venv --exclude=__pycache__ --exclude=images --exclude=diagrams --exclude=mobile-android \
  --exclude=segredos . | ssh prod 'tar xzf - -C /srv/apps/flightmonitor'

ssh prod 'cd /srv/apps/flightmonitor \
  && docker compose -f docker-compose.yml -f docker-compose.prod.yml build \
  && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait && sync'
```

Migration nova? Antes, uma cópia do banco além do dump diário:

```bash
ssh lab 'sudo sh -c "docker exec datasources-postgres pg_dump -U postgres -Fc flightmon \
  > /srv/datasources/dumps/flightmon-antes-$(date +%Y%m%d%H%M).dump"'
```

## Verificar

```bash
ssh prod 'cd /srv/apps/flightmonitor && docker compose -f docker-compose.yml -f docker-compose.prod.yml ps'
ssh prod 'ss -tlnp | grep 8090'          # 127.0.0.1:8090, nunca 0.0.0.0

U=https://prod.tail8feb71.ts.net:10000
curl -o /dev/null -w "%{http_code}\n" $U/                          # 401 — pede senha
curl -o /dev/null -w "%{http_code}\n" $U/healthz                   # 200 — sem senha
curl -o /dev/null -w "%{http_code}\n" $U/api/webhooks/whatsapp     # 403 — passou do nginx, o core recusou
```

A senha do painel foi gerada no `prod` e só existe lá:

```bash
ssh prod 'grep -E "^PAINEL_(USUARIO|SENHA)=" /srv/apps/flightmonitor/.env'
```

## Montado uma vez (registro)

1. Banco: `ssh lab 'bash -s -- flightmon' < dell-lab/91-banco-da-aplicacao.sh` (repositório de
   infraestrutura). Idempotente.
2. `.env` do `prod`: o do desenvolvimento, menos `POSTGRES_*` e `CLOUDFLARE_*`, mais
   `POSTGRES_HOST/DB/USER/PASSWORD` tirados da URL do passo 1 e um `PAINEL_SENHA` novo.
3. Dados do PC migrados por `pg_dump | pg_restore --no-owner --role=flightmon`.
4. `sudo tailscale funnel --bg --https=10000 8090`. Persiste a reboot.

## Webhook da Meta

Com a URL estável, o webhook do WhatsApp fica:

| Campo | Valor |
|---|---|
| Callback URL | `https://prod.tail8feb71.ts.net:10000/api/webhooks/whatsapp` |
| Verify token | o `WHATSAPP_WEBHOOK_VERIFY_TOKEN` do `.env` do `prod` |

É a **única** rota fora da senha: quem chama é o servidor da Meta. Ela se protege pela assinatura
`X-Hub-Signature-256`, então **defina `WHATSAPP_APP_SECRET` antes de cadastrar**.

## O que este desenho não cobre

- **Cair o `lab` derruba o banco**, inclusive no reboot automático diário (~70 s). O core reconecta
  sozinho e o scheduler pega os monitores vencidos.
- **As duas máquinas estão na mesma casa**: queda de luz ou de internet para tudo.
- **O Funnel não tem garantia de banda** — adequado a uso pessoal, que é o caso.

## Alertas no celular

Ver [GUIA-PUSH.md](GUIA-PUSH.md).
