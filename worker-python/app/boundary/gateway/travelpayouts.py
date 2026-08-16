"""Camada 1 da coleta: Travelpayouts Data API.

Varredura ampla e barata. O endpoint /v1/prices/calendar devolve o preco mais
barato de cada dia de um mes inteiro em uma unica chamada.

Duas armadilhas confirmadas em chamada real, ambas tratadas aqui:

  RISCO-006  A API normaliza o aeroporto para o codigo da CIDADE: pedimos GRU e
             ela responde SAO. Gravamos sempre o codigo PEDIDO, para que o
             historico da rota nao se parta em duas. A imprecisao fica
             registrada no campo `source`: TRAVELPAYOUTS e preco de cidade,
             FAST_FLIGHTS e aeroporto confirmado.

  RISCO-007  O parametro de mes e ignorado quando nao ha dados: pedimos 2027-03
             e vieram datas de 2026-08. Nunca confiar no filtro do provider —
             filtramos toda data contra a janela pedida.
"""

import logging
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any

import httpx2

from app.control.busca.portas import ProviderError
from app.schemas import CalendarSearchRequest, CalendarSearchResponse, FlightOffer

logger = logging.getLogger(__name__)

# Ida E VOLTA. Este endpoint IGNORA `one_way` — foi testado contra a API real:
# com `one_way=true`, `one_way=1` ou sem o parametro, ele devolve sempre ofertas
# com `return_at`. Serve para monitor com janela de volta, e so.
BASE_URL = "https://api.travelpayouts.com/v1/prices/calendar"

# SO IDA. O unico endpoint da Travelpayouts que respeita `one_way`. Devolve
# menos datas que o calendario (2 contra 5, no teste que achou o BUG-016) —
# e sao os precos certos, o que vale mais. Ver D-107.
BASE_URL_SO_IDA = "https://api.travelpayouts.com/v2/prices/latest"

SOURCE = "TRAVELPAYOUTS"
TIMEOUT_SEGUNDOS = 20.0


class TravelpayoutsError(ProviderError):
    """Falha ao consultar a fonte. O chamador decide se degrada ou aborta.

    Herda de `ProviderError` para que o roteador trate qualquer camada 1 —
    real ou falsa — pelo mesmo caminho, sem conhecer a implementacao.
    """

    def __init__(self, mensagem: str) -> None:
        super().__init__("travelpayouts", mensagem)


def _meses_da_janela(inicio: date, fim: date) -> list[str]:
    """Meses no formato YYYY-MM cobertos pela janela.

    Uma janela pode cruzar a virada do mes (28/03 a 05/04), e a API so aceita
    um mes por chamada.
    """
    meses: list[str] = []
    ano, mes = inicio.year, inicio.month
    while (ano, mes) <= (fim.year, fim.month):
        meses.append(f"{ano:04d}-{mes:02d}")
        mes += 1
        if mes > 12:
            mes = 1
            ano += 1
    return meses


def _horario_local(valor: Any) -> datetime | None:
    """Converte '2026-08-14T21:45:00-03:00' no horario LOCAL, sem fuso.

    Descartar o offset e proposital: horario de partida e local do aeroporto,
    e o schema do banco guarda `timestamp` sem fuso.
    """
    if not valor:
        return None
    try:
        return datetime.fromisoformat(str(valor)).replace(tzinfo=None)
    except ValueError:
        return None


def _instante(valor: Any) -> datetime | None:
    if not valor:
        return None
    try:
        texto = str(valor).replace("Z", "+00:00")
        return datetime.fromisoformat(texto)
    except ValueError:
        return None


def _preco(valor: Any) -> Decimal | None:
    try:
        preco = Decimal(str(valor))
    except (InvalidOperation, TypeError):
        return None
    return preco if preco > 0 else None


def _para_oferta(dia: str, bruto: dict[str, Any], moeda: str) -> FlightOffer | None:
    try:
        partida = date.fromisoformat(dia)
    except ValueError:
        return None

    preco = _preco(bruto.get("price"))
    if preco is None:
        return None

    volta = _horario_local(bruto.get("return_at"))
    numero = bruto.get("flight_number")

    return FlightOffer(
        departure_date=partida,
        return_date=volta.date() if volta else None,
        price=preco,
        currency=moeda,
        airline=bruto.get("airline"),
        # O calendario diz a companhia e nao diz a agencia.
        agency=None,
        flight_number=str(numero) if numero is not None else None,
        stops=bruto.get("transfers"),
        # Este endpoint nao informa duracao. Nulo diz isso; qualquer numero
        # inventado aqui apareceria na tela como fato.
        duration_minutes=None,
        departure_at=_horario_local(bruto.get("departure_at")),
        # Este endpoint NAO devolve horario de chegada. A primeira versao punha
        # `return_at` aqui — que e a partida da VOLTA, e nao a chegada da ida.
        # Gravava um horario errado no historico; nulo diz a verdade (BUG-016).
        arrival_at=None,
        expires_at=_instante(bruto.get("expires_at")),
        source=SOURCE,
    )


def _inteiro_positivo(valor: Any) -> int | None:
    """Minutos so valem se forem um numero positivo.

    Zero ou negativo nao e "duracao desconhecida", e dado quebrado — e mostrar
    "0h00" na tela seria pior que mostrar nada.
    """
    try:
        n = int(valor)
    except (TypeError, ValueError):
        return None
    return n if n > 0 else None


def _para_oferta_so_ida(bruto: dict[str, Any], moeda: str) -> FlightOffer | None:
    """Mapeia uma entrada do `v2/prices/latest`, que tem outro formato.

    Campos diferentes do calendario: `depart_date` em vez da chave do dicionario,
    `value` em vez de `price`, `number_of_changes` em vez de `transfers`, e
    `gate` no lugar de `airline`.
    """
    try:
        partida = date.fromisoformat(str(bruto.get("depart_date", ""))[:10])
    except ValueError:
        return None

    preco = _preco(bruto.get("value"))
    if preco is None:
        return None

    return FlightOffer(
        departure_date=partida,
        # So ida: nao ha volta, e dizer isso e o proposito deste caminho.
        return_date=None,
        price=preco,
        currency=moeda,
        # Este endpoint nao informa quem OPERA o voo.
        airline=None,
        # ...mas informa quem VENDE, e essa e a referencia que permite ir
        # comprar. Guardada em campo proprio: no `airline` ela quebraria a
        # comparacao com as companhias evitadas do monitor.
        agency=bruto.get("gate") or None,
        flight_number=None,
        stops=bruto.get("number_of_changes"),
        duration_minutes=_inteiro_positivo(bruto.get("duration")),
        departure_at=None,
        arrival_at=None,
        expires_at=None,
        source=SOURCE,
    )


class TravelpayoutsProvider:

    name = "travelpayouts"

    def __init__(self, token: str, base_url: str = BASE_URL) -> None:
        self._token = token
        self._base_url = base_url

    async def buscar(
        self, req: CalendarSearchRequest, cliente: httpx2.AsyncClient | None = None
    ) -> CalendarSearchResponse:
        req.validar_janela()

        if not self._token:
            raise TravelpayoutsError("TRAVELPAYOUTS_TOKEN nao configurado")

        resposta = CalendarSearchResponse(origin=req.origin, destination=req.destination)

        # A fonte tem DOIS endpoints, e eles respondem perguntas diferentes.
        # Perguntar a errada foi o BUG-016: um monitor de somente ida recebia
        # preco de ida E VOLTA, e comparava esse preco com o proprio teto.
        so_ida = req.return_from is None

        proprio = cliente is None
        cliente = cliente or httpx2.AsyncClient(timeout=TIMEOUT_SEGUNDOS)
        try:
            if so_ida:
                brutos = await self._consultar_so_ida(cliente, req, resposta)
                resposta.returned = len(brutos)
                resposta.offers = self._filtrar_so_ida(brutos, req, resposta)
            else:
                brutas: list[tuple[str, dict[str, Any]]] = []
                for mes in _meses_da_janela(req.departure_from, req.departure_to):
                    brutas.extend(await self._consultar_mes(cliente, req, mes, resposta))
                resposta.returned = len(brutas)
                resposta.offers = self._filtrar(brutas, req, resposta)
        finally:
            if proprio:
                await cliente.aclose()

        resposta.kept = len(resposta.offers)
        resposta.offers.sort(key=lambda o: (o.price, o.departure_date))

        if resposta.returned and not resposta.kept:
            resposta.warnings.append(
                "a fonte devolveu precos, mas nenhum caiu dentro da janela pedida"
            )

        return resposta

    async def _consultar_mes(
        self,
        cliente: httpx2.AsyncClient,
        req: CalendarSearchRequest,
        mes: str,
        resposta: CalendarSearchResponse,
    ) -> list[tuple[str, dict[str, Any]]]:
        parametros = {
            "origin": req.origin,
            "destination": req.destination,
            "depart_date": mes,
            "calendar_type": "departure_date",
            "currency": req.currency,
            "token": self._token,
        }

        try:
            r = await cliente.get(self._base_url, params=parametros)
            r.raise_for_status()
            corpo = r.json()
        except httpx2.TimeoutException as e:
            raise TravelpayoutsError(f"timeout ao consultar {mes}") from e
        except httpx2.HTTPStatusError as e:
            raise TravelpayoutsError(
                f"a fonte respondeu HTTP {e.response.status_code} para {mes}"
            ) from e
        except httpx2.HTTPError as e:
            raise TravelpayoutsError(f"falha de rede ao consultar {mes}: {e}") from e
        except ValueError as e:
            raise TravelpayoutsError(f"resposta nao e JSON valido para {mes}") from e

        if not isinstance(corpo, dict) or corpo.get("success") is False:
            motivo = corpo.get("error") if isinstance(corpo, dict) else corpo
            raise TravelpayoutsError(f"a fonte recusou a consulta de {mes}: {motivo}")

        dados = corpo.get("data") or {}
        if not isinstance(dados, dict):
            return []

        self._registrar_codigo_do_provider(dados, req, resposta)
        return list(dados.items())

    async def _consultar_so_ida(
        self,
        cliente: httpx2.AsyncClient,
        req: CalendarSearchRequest,
        resposta: CalendarSearchResponse,
    ) -> list[dict[str, Any]]:
        """Precos de SO IDA, no unico endpoint que respeita `one_way`.

        Devolve menos datas que o calendario. E o que a fonte tem: preferimos
        dois precos certos a cinco errados — e o preco errado nao era so
        impreciso, era de outro produto.
        """
        parametros = {
            "origin": req.origin,
            "destination": req.destination,
            "beginning_of_period": req.departure_from.strftime("%Y-%m-01"),
            "period_type": "month",
            "one_way": "true",
            "currency": req.currency,
            "limit": 1000,
            "token": self._token,
        }

        try:
            r = await cliente.get(BASE_URL_SO_IDA, params=parametros)
            r.raise_for_status()
            corpo = r.json()
        except httpx2.TimeoutException as e:
            raise TravelpayoutsError("timeout ao consultar precos de so ida") from e
        except httpx2.HTTPStatusError as e:
            raise TravelpayoutsError(
                f"a fonte respondeu HTTP {e.response.status_code} para so ida"
            ) from e
        except httpx2.HTTPError as e:
            raise TravelpayoutsError(f"falha de rede ao consultar so ida: {e}") from e
        except ValueError as e:
            raise TravelpayoutsError("resposta de so ida nao e JSON valido") from e

        if not isinstance(corpo, dict) or corpo.get("success") is False:
            motivo = corpo.get("error") if isinstance(corpo, dict) else corpo
            raise TravelpayoutsError(f"a fonte recusou a consulta de so ida: {motivo}")

        dados = corpo.get("data")
        if not isinstance(dados, list):
            return []

        brutos = [d for d in dados if isinstance(d, dict)]
        if brutos:
            resposta.provider_origin = brutos[0].get("origin")
            resposta.provider_destination = brutos[0].get("destination")

        return brutos

    def _filtrar_so_ida(
        self,
        brutos: list[dict[str, Any]],
        req: CalendarSearchRequest,
        resposta: CalendarSearchResponse,
    ) -> list[FlightOffer]:
        fora_da_janela = 0
        com_volta = 0
        ofertas: list[FlightOffer] = []

        for bruto in brutos:
            # Cinto e suspensorio: o endpoint promete respeitar `one_way`, mas o
            # BUG-016 nasceu de confiar numa promessa dessas. Oferta com volta
            # num pedido de so ida e descartada, venha de onde vier.
            if str(bruto.get("return_date") or "").strip():
                com_volta += 1
                continue

            oferta = _para_oferta_so_ida(bruto, req.currency)
            if oferta is None:
                continue

            # RISCO-007: nunca confiar que o provider respeitou o periodo pedido.
            if not (req.departure_from <= oferta.departure_date <= req.departure_to):
                fora_da_janela += 1
                continue

            if req.max_stops is not None and oferta.stops is not None:
                if oferta.stops > req.max_stops:
                    fora_da_janela += 1
                    continue

            ofertas.append(oferta)

        if com_volta:
            resposta.warnings.append(
                f"{com_volta} oferta(s) de ida e volta descartada(s): este monitor e somente ida"
            )
        if fora_da_janela:
            resposta.warnings.append(
                f"{fora_da_janela} oferta(s) descartada(s) por estarem fora dos criterios pedidos"
            )
        if not ofertas and brutos:
            resposta.warnings.append(
                "a fonte devolveu precos de so ida, mas nenhum dentro da janela do monitor"
            )

        return ofertas

    def _registrar_codigo_do_provider(
        self,
        dados: dict[str, Any],
        req: CalendarSearchRequest,
        resposta: CalendarSearchResponse,
    ) -> None:
        """RISCO-006: guarda o codigo devolvido e avisa quando difere do pedido."""
        primeiro = next(iter(dados.values()), None)
        if not isinstance(primeiro, dict):
            return

        devolvido_origem = primeiro.get("origin")
        devolvido_destino = primeiro.get("destination")
        resposta.provider_origin = devolvido_origem
        resposta.provider_destination = devolvido_destino

        if devolvido_origem and devolvido_origem != req.origin:
            aviso = (
                f"a fonte respondeu com o codigo de cidade {devolvido_origem} "
                f"para a origem {req.origin}: o preco pode ser de outro aeroporto da regiao"
            )
            if aviso not in resposta.warnings:
                resposta.warnings.append(aviso)

    def _filtrar(
        self,
        brutas: list[tuple[str, dict[str, Any]]],
        req: CalendarSearchRequest,
        resposta: CalendarSearchResponse,
    ) -> list[FlightOffer]:
        agora = datetime.now().astimezone()
        fora_da_janela = 0
        vencidas = 0
        ofertas: list[FlightOffer] = []

        for dia, bruto in brutas:
            if not isinstance(bruto, dict):
                continue

            oferta = _para_oferta(dia, bruto, req.currency)
            if oferta is None:
                continue

            # RISCO-007: nunca confiar que o provider respeitou o mes pedido.
            if not (req.departure_from <= oferta.departure_date <= req.departure_to):
                fora_da_janela += 1
                continue

            if req.return_from is not None:
                if oferta.return_date is None:
                    fora_da_janela += 1
                    continue
                if not (req.return_from <= oferta.return_date <= req.return_to):
                    fora_da_janela += 1
                    continue

            if req.max_stops is not None and oferta.stops is not None:
                if oferta.stops > req.max_stops:
                    fora_da_janela += 1
                    continue

            # RISCO-003: preco cacheado ja vencido nao serve nem como candidato.
            if oferta.expires_at is not None and oferta.expires_at < agora:
                vencidas += 1
                continue

            ofertas.append(oferta)

        if fora_da_janela:
            resposta.warnings.append(
                f"{fora_da_janela} ofertas descartadas por estarem fora dos criterios pedidos"
            )
        if vencidas:
            resposta.warnings.append(f"{vencidas} ofertas descartadas por preco ja vencido")

        return ofertas
