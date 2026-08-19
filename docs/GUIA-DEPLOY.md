# Guia — subir em produção

> Etapa E4.4. O que **você** precisa fazer, e por quê.
> Decisões em [D-111](DECISOES.md).

## O desenho, em uma frase

O sistema roda **na sua máquina**, e um túnel da Cloudflare o publica na internet — sem abrir
nenhuma porta no seu roteador.

```
Internet ──HTTPS──▶ Cloudflare ──túnel──▶ painel (nginx) ──▶ core ──▶ worker
                                            │
                                       senha básica,
                                    exceto no webhook
```

**Por que túnel, e não redirecionamento de porta:** IP residencial muda, e boa parte dos
provedores brasileiros usa **CGNAT** — que impede redirecionamento de porta mesmo com IP fixo. O
túnel não depende de nenhum dos dois: a conexão sai de dentro para fora.

E o HTTPS termina na Cloudflare, então o webhook da Meta — que exige TLS — funciona sem
certificado nenhum aqui dentro.

---

## O que providenciar

### 1. Uma senha para o painel

Qualquer senha longa. Ela protege **tudo**: a tela e a API. Sem ela, quem chegar na URL edita
seus monitores.

### 2. O túnel na Cloudflare

Você já tem `devleoholandaportfolio.com.br`. Se o domínio ainda não estiver na Cloudflare, é
preciso apontar os nameservers para lá (gratuito, e leva algumas horas para propagar).

1. Acesse **[one.dash.cloudflare.com](https://one.dash.cloudflare.com)** → *Networks* → *Tunnels*
2. **Create a tunnel** → tipo **Cloudflared** → dê um nome (ex.: `flightmonitor`)
3. A tela mostra um comando de instalação com um **token longo**. Você não vai rodar esse
   comando — só copie o token (a parte depois de `--token`)
4. Em **Public Hostnames**, adicione:

   | Campo | Valor |
   |---|---|
   | Subdomain | `voos` |
   | Domain | `devleoholandaportfolio.com.br` |
   | Type | `HTTP` |
   | URL | `painel:80` |

   `painel:80` é o nome do serviço na rede do compose — o túnel fala com ele por dentro, e é por
   isso que nada precisa estar publicado no host.

### 3. Colocar as duas coisas no `.env`

```dotenv
# --- Produção (E4.4) ---
PAINEL_USUARIO=leonardo
PAINEL_SENHA=uma-senha-longa-de-verdade
CLOUDFLARE_TUNNEL_TOKEN=cole-aqui-o-token-do-passo-2
```

O `.env` é **gitignored** — nada disso vai para o repositório.

---

## Subir

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Painel em **https://voos.devleoholandaportfolio.com.br** (e em `http://localhost:8090`).

O compose de produção **recusa a subir** sem `PAINEL_SENHA` ou sem o token do túnel. É de
propósito: um painel aberto na internet por esquecimento é pior que um deploy que falha alto.

### O que muda em relação ao desenvolvimento

| | Desenvolvimento | Produção |
|---|---|---|
| Postgres e RabbitMQ | portas publicadas | **só na rede interna** |
| Worker | porta publicada | **só na rede interna** |
| Painel | aberto, em `0.0.0.0` | **com senha**, em `127.0.0.1` + túnel |
| Canário das fontes | desligado | **ligado** |
| Memória do core | sem limite | 768 MB |

---

## Fechar o webhook da Meta

Com a URL estável, dá para fechar a última ponta da Fase 1 — `DELIVERED` e `READ` deixam de
depender de nada.

1. No **[Meta for Developers](https://developers.facebook.com)** → seu app → *WhatsApp* →
   *Configuration* → **Webhook** → *Edit*
2. Preencha:

   | Campo | Valor |
   |---|---|
   | Callback URL | `https://voos.devleoholandaportfolio.com.br/api/webhooks/whatsapp` |
   | Verify token | o mesmo `WHATSAPP_WEBHOOK_VERIFY_TOKEN` do seu `.env` |

3. **Verify and save.** A Meta chama a URL na hora; se o token bater, o cadastro é aceito
4. Em **Webhook fields**, assine `messages`

O webhook é a **única** rota fora da senha do painel, e não há escolha: quem chama é o servidor da
Meta, que não tem como autenticar por senha básica. Ele se protege por outro meio — a assinatura
`X-Hub-Signature-256`, conferida com o `WHATSAPP_APP_SECRET`. **Sem esse segredo configurado, o
endpoint aceita qualquer POST** — defina-o antes de expor.

---

## O que este desenho não resolve

**O sistema só roda enquanto sua máquina estiver ligada.** Foi uma escolha consciente, mas tem
consequência concreta: um monitor com intervalo de 6 horas perde as varreduras da madrugada se o
PC estiver desligado, e o sistema existe justamente para vigiar quando você não está olhando.

O que **não** se perde: o próximo horário fica gravado no banco, então ao religar o scheduler
encontra os monitores vencidos e varre. Você perde a oportunidade daquela janela, e não o
histórico.

Se um dia isso incomodar, o caminho é o mesmo compose numa VPS de 2 GB (~R$ 30/mês) — os 650 MB
cabem com folga, e nada além do `.env` muda.

---

## Verificar que subiu inteiro

```bash
# os seis containers, todos saudáveis
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# o painel pede senha
curl -o /dev/null -w "%{http_code}\n" https://voos.devleoholandaportfolio.com.br/     # 401
curl -o /dev/null -w "%{http_code}\n" -u leonardo:SENHA https://voos.../             # 200

# o webhook NÃO pede senha (a Meta não saberia responder)
curl -o /dev/null -w "%{http_code}\n" https://voos.../api/webhooks/whatsapp          # 403, não 401

# o canário está ligado e as fontes respondem no formato esperado
docker compose logs core | grep canario
```

`403` no webhook é o resultado certo: significa que passou pelo nginx e o **core** recusou o token
de verificação. `401` ali seria o nginx barrando a Meta.
