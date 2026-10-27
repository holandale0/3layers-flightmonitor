# Guia — alertas no aplicativo (push)

> O que **você** precisa fazer para os alertas chegarem como notificação no celular.
> Decisões em [D-113](DECISOES.md).

## O desenho

```
core (prod) ──HTTP v1──▶ Firebase Cloud Messaging ──▶ app Android ──toque──▶ painel no monitor
     ▲                                                     │
     └──────── POST /api/recipients/{id}/push-devices ◀────┘  (o app se registra)
```

O app (`mobile-android/`) é o próprio painel num WebView. Ele só acrescenta duas coisas: a senha
lembrada e o botão **Receber neste aparelho**, na tela de destinatários.

Sem o Firebase configurado, o app funciona igual; só não oferece o botão. Sem a chave no
servidor, o core sobe igual, e os alertas PUSH falham com a mensagem dizendo o que falta.

---

## 1. Criar o projeto no Firebase (uma vez)

1. Acesse **[console.firebase.google.com](https://console.firebase.google.com)** → **Adicionar
   projeto** → nome `flightmonitor` (o Google Analytics pode ficar desligado)
2. No projeto: **Adicionar app** → **Android**
   - Nome do pacote: **`com.flightmonitor.app`** (exatamente este: é o `applicationId` do app)
   - Apelido: Flight Monitor. O SHA-1 não é necessário para push
3. Baixe o **`google-services.json`** e coloque em `mobile-android/app/google-services.json`.
   Ele está no `.gitignore`: identifica o seu projeto, e o repositório é público

## 2. Gerar a chave do servidor

1. No console do Firebase: ⚙️ **Configurações do projeto** → **Contas de serviço**
2. **Gerar nova chave privada** → baixa um JSON
3. Envie para o `prod`, sem passar pelo repositório:

```bash
scp ~/Downloads/flightmonitor-*.json prod:/srv/apps/flightmonitor/segredos/firebase.json
ssh prod 'chmod 644 /srv/apps/flightmonitor/segredos/firebase.json && sync'
```

O `644` é para o usuário sem privilégio do container conseguir ler. Quem protege o arquivo no host
é a pasta de cima, `/srv/apps/flightmonitor`, que é `750` do `ubuntu`.

## 3. Ligar o canal no servidor

No `/srv/apps/flightmonitor/.env` do `prod`:

```dotenv
# Vários canais ao mesmo tempo: cada destinatário recebe por todos que o alcançam
NOTIFICATION_CHANNELS=EMAIL,PUSH
# Caminho DENTRO do container (a pasta segredos/ entra em /run/segredos)
FCM_CREDENCIAIS=/run/segredos/firebase.json
```

```bash
ssh prod 'cd /srv/apps/flightmonitor && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d core'
ssh prod 'docker logs flightmon-core 2>&1 | grep "canal PUSH"'   # "canal PUSH com a conta de servico ..."
```

## 4. Gerar e instalar o app

```bash
cd mobile-android
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/flightmonitor-debug.apk
```

Instale no celular (por cabo com `adb install`, ou mandando o arquivo e abrindo no aparelho; o
Android pede para permitir "instalar apps desconhecidos"). Na primeira abertura, o app pede o
usuário e a senha do painel (`PAINEL_USUARIO` e `PAINEL_SENHA` do `.env` do `prod`):

```bash
ssh prod 'grep -E "^PAINEL_(USUARIO|SENHA)=" /srv/apps/flightmonitor/.env'
```

## 5. Registrar o celular

No app: **Destinatários** → no seu cartão, **Receber neste aparelho** → permitir notificações.
O aparelho aparece na lista do destinatário. Toque em **Testar**: a notificação deve chegar em
segundos.

---

## Quando não chega

| O que aparece | Causa | O que fazer |
|---|---|---|
| Botão "Receber neste aparelho" não aparece | APK gerado sem `google-services.json` | Passo 1.3 e gerar de novo |
| "Permissão de notificação negada" | Recusada no diálogo do Android | Configurações › Apps › Flight Monitor › Notificações |
| Teste falha com "sem conta de servico" | `FCM_CREDENCIAIS` vazio ou arquivo ilegível | Passos 2 e 3; o log do core diz qual dos dois |
| Teste falha com `HTTP 403` | API do FCM desligada no projeto, ou chave revogada | Console do Google Cloud → APIs → *Firebase Cloud Messaging API* → ativar |
| Aparelho "desligado" | O Firebase recusou o token (app desinstalado, dados limpos) | Tocar em "Receber neste aparelho" de novo, que reativa |
| Chega atrasado com a tela desligada | Economia de bateria do fabricante | Configurações › Bateria › Flight Monitor › sem restrição |

## Para outro endereço

O app aponta para `https://prod.tail8feb71.ts.net:10000`. Para gerar apontando para outro:

```bash
./gradlew assembleDebug -PpainelUrl=https://lab.tail8feb71.ts.net
```
