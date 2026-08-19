# =============================================================================
# frontend-vue — etapa E4.2
#
# O Vue e compilado para arquivos estaticos; nao ha Node na imagem final. O que
# roda em producao e um nginx servindo HTML, CSS e JS — nada mais.
# =============================================================================

# ----------------------------------------------------------------- construcao
FROM node:22-alpine AS construcao
WORKDIR /construcao

# `npm ci` e nao `npm install`: instala exatamente o que esta no lock, sem
# atualizar nada. Build de imagem que resolve versao sozinho nao e reproduzivel.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# `npm run build` roda o vue-tsc antes do vite: erro de tipo quebra a imagem em
# vez de virar defeito em producao.
RUN npm run build

# ------------------------------------------------------------------- execucao
FROM nginx:1.27-alpine

# `htpasswd`, para gerar o arquivo de senha no arranque a partir do ambiente.
# Gerar aqui e nao versionar o hash: senha em repositorio e senha vazada, e
# este repositorio e publico.
RUN apk add --no-cache apache2-utils

COPY --from=construcao /construcao/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# O nginx executa tudo em /docker-entrypoint.d/*.sh antes de subir.
COPY 20-senha-do-painel.sh /docker-entrypoint.d/20-senha-do-painel.sh
RUN chmod +x /docker-entrypoint.d/20-senha-do-painel.sh

EXPOSE 80
