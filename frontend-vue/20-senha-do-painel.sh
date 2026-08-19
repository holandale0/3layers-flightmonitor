#!/bin/sh
# Protege o painel com senha, quando houver senha — etapa E4.4.
#
# # Por que no arranque, e nao na imagem
#
# A senha vem do ambiente e nunca entra no repositorio nem na imagem. Imagem e
# artefato compartilhavel; senha nao. O arquivo gerado vive so no container.
#
# # Por que condicional
#
# Sem PAINEL_SENHA o painel fica aberto — que e o certo para desenvolvimento,
# onde ele escuta em localhost e pedir senha a cada F5 so atrapalha. Em
# producao, o mesmo compose exige a variavel (ver docker-compose.prod.yml).
#
# O `include` sempre existe; o que muda e o conteudo. nginx nao tem `if` para
# isso, e gerar o trecho e mais honesto que duplicar o arquivo de configuracao.
set -e

ARQUIVO_AUTH=/etc/nginx/auth.conf

if [ -n "$PAINEL_SENHA" ]; then
    USUARIO="${PAINEL_USUARIO:-admin}"
    htpasswd -bc /etc/nginx/.htpasswd "$USUARIO" "$PAINEL_SENHA" 2>/dev/null

    cat > "$ARQUIVO_AUTH" <<EOF
auth_basic "Flight Monitor";
auth_basic_user_file /etc/nginx/.htpasswd;
EOF
    echo "painel protegido por senha (usuario: $USUARIO)"
else
    # Vazio, e nao ausente: o `include` do nginx.conf falharia sem o arquivo.
    : > "$ARQUIVO_AUTH"
    echo "painel SEM senha: defina PAINEL_SENHA para proteger"
fi
