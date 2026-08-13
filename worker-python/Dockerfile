# =============================================================================
# worker-python — etapa E4.2
#
# Duas etapas, como o core: a primeira instala as dependencias num venv, a
# segunda copia so o venv pronto. Evita levar compilador e cabecalhos de
# desenvolvimento para a imagem final.
# =============================================================================

# ----------------------------------------------------------------- construcao
FROM python:3.14-slim AS construcao

# `build-essential` so existe nesta etapa: algumas rodas precisam compilar, e
# nada disso precisa sobreviver ate a execucao.
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

RUN python -m venv /venv
ENV PATH="/venv/bin:$PATH"

# Primeiro so o requirements, pelo mesmo motivo do pom no core: enquanto ele nao
# mudar, esta camada e reaproveitada.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ------------------------------------------------------------------- execucao
FROM python:3.14-slim

RUN groupadd -r flightmon && useradd -r -g flightmon flightmon

COPY --from=construcao /venv /venv
ENV PATH="/venv/bin:$PATH" \
    # Sem .pyc no container: a imagem e descartavel e o cache so ocupa espaco.
    PYTHONDONTWRITEBYTECODE=1 \
    # Log sai na hora, e nao quando o buffer encher. Sem isto, `docker logs` de
    # um worker travado aparece vazio — justamente quando mais se precisa dele.
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY app ./app
USER flightmon

EXPOSE 8001

# Uma worker so, de proposito: o worker e stateless e o paralelismo de verdade
# vem de subir mais containers. Varios processos aqui dentro brigariam pela
# mesma cota das fontes externas sem nenhum controle (RISCO-004).
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
