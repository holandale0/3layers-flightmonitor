"""Interpretacao por LLM — etapa E3.1.

# O que este provider faz, e o que ele NAO faz

Ele extrai **campos**, e nada mais. Nao decide se o preco e bom, nao escolhe
aeroporto, nao cria monitor. A regra 2 da secao 3 do PLANO-DE-ACAO continua
valendo: o worker e um especialista, e aqui a especialidade e entender o pedido.

**O modelo nao devolve codigo IATA.** Ele devolve o nome da cidade, e a
traducao para codigo acontece na tabela de `aeroportos.py`. Modelos erram codigo
de aeroporto com frequencia — sao milhares, e GIG e SDU diferem por uma letra —
enquanto acertam nome de cidade quase sempre. Cada um no que e bom.

# Por que a saida passa pelo mesmo `MonitorIntent`

Porque quem consome nao pode saber quem interpretou. Trocar o modelo, ou cair
para as regras, nao muda o contrato — e o `provider` na resposta diz o que
aconteceu, para quem quiser saber.
"""

import json
import logging
from datetime import date

import httpx2

from app.control.nlp.aeroportos import resolver
from app.control.nlp.portas import IntentError
from app.schemas import IntentRequest, MonitorIntent

logger = logging.getLogger(__name__)

NOME = "claude"
URL = "https://api.anthropic.com/v1/messages"
VERSAO_API = "2023-06-01"
TIMEOUT_SEGUNDOS = 25.0

INSTRUCAO = """Voce extrai dados de pedidos de passagem aerea escritos em portugues do Brasil.

Devolva APENAS um objeto JSON, sem texto em volta e sem cerca de codigo, com estas chaves:

  origem            nome da CIDADE de partida, ou null
  destino           nome da CIDADE de chegada, ou null
  data_inicio       primeiro dia possivel de partida, formato AAAA-MM-DD, ou null
  data_fim          ultimo dia possivel de partida, formato AAAA-MM-DD, ou null
  permanencia_min   dias minimos de viagem, inteiro ou null
  permanencia_max   dias maximos de viagem, inteiro ou null
  preco_maximo      numero, sem simbolo de moeda, ou null
  escalas_maximas   inteiro ou null
  prefere_direto    true quando a pessoa demonstra preferir voo direto
  passageiros       inteiro ou null
  evitar_companhias lista de nomes de companhia a evitar
  observacoes       lista de frases curtas sobre o que ficou ambiguo

Regras que voce NAO pode quebrar:

- NUNCA invente. Campo que o texto nao disser vai null, e o motivo vai em observacoes.
- Use NOME de cidade, nunca codigo de aeroporto. "Sao Paulo", nao "GRU".
- Datas sempre no futuro em relacao a data de hoje informada.
- Mes sem ano significa a proxima ocorrencia daquele mes.
- "voo direto" e preferencia (prefere_direto=true), nao limite. So use
  escalas_maximas=0 se a pessoa disser que aceita SOMENTE voo direto.
"""


class ClaudeIntentProvider:
    """Interpreta com a API da Anthropic."""

    name = NOME

    def __init__(self, api_key: str, modelo: str) -> None:
        self._api_key = api_key
        self._modelo = modelo

    async def interpretar(
        self, req: IntentRequest, cliente: httpx2.AsyncClient | None = None
    ) -> MonitorIntent:
        if not self._api_key:
            raise IntentError(NOME, "ANTHROPIC_API_KEY nao configurada")

        hoje = req.hoje or date.today()
        proprio = cliente is None
        cliente = cliente or httpx2.AsyncClient(timeout=TIMEOUT_SEGUNDOS)

        try:
            resposta = await cliente.post(
                URL,
                headers={
                    "x-api-key": self._api_key,
                    "anthropic-version": VERSAO_API,
                    "content-type": "application/json",
                },
                json={
                    "model": self._modelo,
                    "max_tokens": 1024,
                    "system": INSTRUCAO,
                    "messages": [{
                        "role": "user",
                        "content": f"Hoje e {hoje.isoformat()}.\n\nPedido: {req.texto}",
                    }],
                },
            )
        except httpx2.TimeoutException as e:
            raise IntentError(NOME, "timeout ao consultar o modelo") from e
        except httpx2.HTTPError as e:
            raise IntentError(NOME, f"falha de rede: {e}") from e
        finally:
            if proprio:
                await cliente.aclose()

        if resposta.status_code != 200:
            raise IntentError(NOME, f"HTTP {resposta.status_code}: {resposta.text[:200]}")

        return self._converter(resposta.json(), req)

    def _converter(self, corpo: dict, req: IntentRequest) -> MonitorIntent:
        try:
            texto = "".join(
                bloco.get("text", "")
                for bloco in corpo.get("content", [])
                if bloco.get("type") == "text"
            )
            dados = json.loads(self._limpar(texto))
        except (ValueError, TypeError, AttributeError) as e:
            raise IntentError(NOME, f"resposta do modelo nao e JSON: {e}") from e

        avisos = list(dados.get("observacoes") or [])

        # A traducao para IATA acontece AQUI, e nao no modelo.
        origem = resolver(dados.get("origem")) or resolver(req.origem_padrao)
        destino = resolver(dados.get("destino"))

        if dados.get("origem") and not resolver(dados.get("origem")):
            avisos.append(f"nao conheco o aeroporto de {dados['origem']}")
        if dados.get("destino") and not destino:
            avisos.append(f"nao conheco o aeroporto de {dados['destino']}")

        intent = MonitorIntent(
            origin=origem,
            destination=destino,
            departure_from=self._data(dados.get("data_inicio")),
            departure_to=self._data(dados.get("data_fim")),
            min_stay_days=dados.get("permanencia_min"),
            max_stay_days=dados.get("permanencia_max"),
            max_price=dados.get("preco_maximo"),
            max_stops=dados.get("escalas_maximas"),
            prefere_voo_direto=bool(dados.get("prefere_direto")),
            passengers=dados.get("passageiros"),
            avoided_airlines=list(dados.get("evitar_companhias") or []),
            label=f"Viagem para {destino}" if destino else None,
            provider=NOME,
            avisos=avisos,
        )

        essenciais = [
            intent.origin, intent.destination,
            intent.departure_from, intent.departure_to, intent.max_price,
        ]
        intent.confianca = round(sum(1 for c in essenciais if c) / len(essenciais), 2)
        for faltou in intent.faltando():
            intent.avisos.append(f"nao consegui identificar: {faltou}")

        return intent

    def _limpar(self, texto: str) -> str:
        """Tira a cerca de codigo que o modelo as vezes coloca, apesar do pedido."""
        limpo = texto.strip()
        if limpo.startswith("```"):
            limpo = limpo.split("\n", 1)[-1]
            limpo = limpo.rsplit("```", 1)[0]
        return limpo.strip()

    def _data(self, valor) -> date | None:
        if not valor:
            return None
        try:
            return date.fromisoformat(str(valor))
        except ValueError:
            logger.debug("data ilegivel devolvida pelo modelo: %r", valor)
            return None
