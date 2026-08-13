"""Camada 2: confirmacao via Google Flights (biblioteca fast-flights).

# ⚠️ ESTE E O COMPONENTE MAIS FRAGIL DO SISTEMA

Nao existe contrato aqui. A biblioteca decodifica o protobuf que o Google usa
internamente na URL do Google Flights e faz parsing do HTML da resposta. Nada
disso e API publica: o Google pode mudar sem aviso, sem versionamento e sem
depreciacao. O proprio README da biblioteca relativiza a confiabilidade.

A instabilidade nao e hipotetica. Entre a versao 2.x e a 3.x a propria API da
biblioteca mudou por completo — `FlightData` e `Result`, presentes em
praticamente todo tutorial na internet, simplesmente deixaram de existir. Este
adaptador foi escrito inspecionando a biblioteca instalada, e nao seguindo
documentacao.

# Como o sistema se protege

1. TUDO aqui e embrulhado em ProviderError. Nenhuma excecao da biblioteca
   escapa para o resto do worker.
2. Este provider fica atras de uma `ConfirmationChain`, que tenta o proximo da
   fila e, se todos falharem, devolve resultado DEGRADADO em vez de erro.
3. Existe uma chave de desligamento em configuracao (`FASTFLIGHTS_ENABLED`).
   Quando quebrar em producao, desliga-se por variavel de ambiente, sem deploy.
4. O `/health` reporta a prontidao desta camada, entao a queda vira sinal
   visivel e nao silencio.

# O que perdemos quando esta camada cai

O sistema continua varrendo, gravando historico e alertando — apenas sem
companhia, escalas e horarios, e sem filtrar o falso-positivo de preco
cacheado. Degrada, nao morre. Ver docs/PLANO-DE-ACAO.md secao 4.
"""

import logging
import re
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any

import anyio

from app.control.busca.portas import ProviderError
from app.schemas import ConfirmedOffer, ConfirmRequest

logger = logging.getLogger(__name__)

SOURCE = "FAST_FLIGHTS"
NOME = "fast-flights"
TIMEOUT_SEGUNDOS = 45.0


def _instante(dt: Any) -> datetime | None:
    """Converte o SimpleDatetime da biblioteca em datetime local, sem fuso.

    Formato observado: `.date` como [ano, mes, dia] e `.time` como [hora, minuto].

    ATENCAO — em consulta real a biblioteca devolveu `time=[None, 45]`: minuto
    presente, HORA AUSENTE. Ou seja, o parser dela falha parcialmente sem avisar.
    Por isso qualquer desvio do formato devolve None em vez de estourar: um
    horario ilegivel nao pode invalidar a confirmacao de um preco correto.
    """
    if dt is None:
        return None
    try:
        ano, mes, dia = dt.date
        hora, minuto = dt.time
        if None in (ano, mes, dia, hora, minuto):
            return None
        return datetime(int(ano), int(mes), int(dia), int(hora), int(minuto))
    except (AttributeError, TypeError, ValueError):
        return None


def _nome_da_cia(item: Any) -> str | None:
    """Extrai o nome da companhia.

    A anotacao de tipo da biblioteca promete `list[Airline]`, mas em execucao
    real vem `list[str]`: ['Tap Air Portugal']. Tratamos os dois formatos, para
    o adaptador sobreviver caso a proxima versao volte atras.
    """
    if item is None:
        return None
    nome = getattr(item, "name", None)
    if isinstance(nome, str) and nome.strip():
        return nome.strip()
    if isinstance(item, str) and item.strip():
        return item.strip()
    return None


def _codigo_da_cia(bruto: Any) -> str | None:
    """O codigo IATA da companhia vem no atributo `type` — nome que nao sugere isso.

    Por ser um mapeamento nao obvio e nao documentado, so aceitamos o valor se
    ele realmente parecer um codigo de companhia. Se a biblioteca passar a usar
    `type` para outra coisa, devolvemos None em vez de gravar lixo.
    """
    valor = getattr(bruto, "type", None)
    if isinstance(valor, str) and re.fullmatch(r"[A-Z0-9]{2,3}", valor.strip()):
        return valor.strip()
    return None


def _preco(valor: Any) -> Decimal | None:
    try:
        preco = Decimal(str(valor))
    except (InvalidOperation, TypeError):
        return None
    return preco if preco > 0 else None


def _dividir_ida_volta(pernas: list[Any], destino: str) -> tuple[list[Any], list[Any]]:
    """Separa as pernas de ida das de volta.

    A biblioteca devolve todas as pernas numa lista unica, sem marcar a direcao.
    A ida termina na primeira perna que chega ao destino; o resto e volta. Sem
    isso, um voo GRU->LIS->GRU com conexao apareceria como tendo 3 escalas.
    """
    for i, perna in enumerate(pernas):
        codigo = getattr(getattr(perna, "to_airport", None), "code", None)
        if codigo and codigo.upper() == destino.upper():
            return pernas[: i + 1], pernas[i + 1 :]
    return pernas, []


class FastFlightsProvider:
    """Confirma um candidato consultando o Google Flights."""

    name = NOME

    def __init__(self, timeout: float = TIMEOUT_SEGUNDOS) -> None:
        self._timeout = timeout

    async def confirm(self, req: ConfirmRequest) -> ConfirmedOffer | None:
        # A biblioteca e sincrona e faz I/O de rede. Sem jogar para uma thread,
        # ela travaria o event loop do worker inteiro durante a consulta.
        try:
            with anyio.fail_after(self._timeout):
                return await anyio.to_thread.run_sync(self._consultar, req)
        except TimeoutError as e:
            raise ProviderError(NOME, f"timeout apos {self._timeout:.0f}s") from e

    def _consultar(self, req: ConfirmRequest) -> ConfirmedOffer | None:
        resultados = self._buscar_no_google(req)
        if not resultados:
            return None
        return self._melhor_oferta(resultados, req)

    def _buscar_no_google(self, req: ConfirmRequest) -> list[Any]:
        # Import tardio: se a biblioteca quebrar na importacao, a falha fica
        # contida nesta camada em vez de impedir o worker de subir.
        try:
            from fast_flights import FlightQuery, Passengers, create_query, get_flights
            from fast_flights.exceptions import FlightsNotFound
        except ImportError as e:
            raise ProviderError(NOME, f"biblioteca indisponivel: {e}") from e

        pernas = [
            FlightQuery(
                date=req.departure_date.isoformat(),
                from_airport=req.origin,
                to_airport=req.destination,
                max_stops=req.max_stops,
            )
        ]
        if req.return_date is not None:
            pernas.append(
                FlightQuery(
                    date=req.return_date.isoformat(),
                    from_airport=req.destination,
                    to_airport=req.origin,
                    max_stops=req.max_stops,
                )
            )

        try:
            query = create_query(
                flights=pernas,
                trip="round-trip" if req.return_date else "one-way",
                seat="economy",
                passengers=Passengers(adults=req.passengers),
                currency=req.currency,
                max_stops=req.max_stops,
            )
            resultado = get_flights(query)
        except FlightsNotFound:
            # Consultamos e nao ha voo assim. Nao e falha: e resposta.
            return []
        except Exception as e:
            # Proposital: a biblioteca nao documenta suas excecoes, e o modo de
            # falha mais provavel e uma mudanca no formato do Google, que se
            # manifesta como AttributeError, IndexError ou KeyError vindos das
            # entranhas do parser. Deixar qualquer uma escapar derrubaria a
            # varredura inteira por causa da camada opcional.
            raise ProviderError(NOME, f"{type(e).__name__}: {e}") from e

        return list(resultado or [])

    def _melhor_oferta(self, resultados: list[Any], req: ConfirmRequest) -> ConfirmedOffer | None:
        candidatas: list[tuple[Decimal, Any]] = []
        for bruto in resultados:
            preco = _preco(getattr(bruto, "price", None))
            if preco is not None:
                candidatas.append((preco, bruto))

        if not candidatas:
            return None

        preco, melhor = min(candidatas, key=lambda p: p[0])
        pernas = list(getattr(melhor, "flights", None) or [])
        ida, _volta = _dividir_ida_volta(pernas, req.destination)

        escalas = max(0, len(ida) - 1) if ida else None
        if req.max_stops is not None and escalas is not None and escalas > req.max_stops:
            return None

        primeira = ida[0] if ida else None
        ultima = ida[-1] if ida else None

        cias = [n for n in (_nome_da_cia(c) for c in getattr(melhor, "airlines", None) or []) if n]

        return ConfirmedOffer(
            departure_date=req.departure_date,
            return_date=req.return_date,
            price=preco,
            currency=req.currency,
            airline=", ".join(cias) if cias else None,
            airline_code=_codigo_da_cia(melhor),
            stops=escalas,
            duration_minutes=self._duracao(ida),
            departure_at=_instante(getattr(primeira, "departure", None)),
            arrival_at=_instante(getattr(ultima, "arrival", None)),
            departure_airport=getattr(getattr(primeira, "from_airport", None), "code", None),
            arrival_airport=getattr(getattr(ultima, "to_airport", None), "code", None),
            source=SOURCE,
        )

    @staticmethod
    def _duracao(pernas: list[Any]) -> int | None:
        total = 0
        achou = False
        for perna in pernas:
            duracao = getattr(perna, "duration", None)
            if isinstance(duracao, int) and duracao > 0:
                total += duracao
                achou = True
        return total if achou else None


def _validar_data_futura(req: ConfirmRequest) -> None:
    """O Google nao devolve preco de data passada; melhor falhar antes de gastar rede."""
    if req.departure_date < date.today():
        raise ProviderError(NOME, "data de partida ja passou")
