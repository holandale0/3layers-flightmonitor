"""Fontes falsas, para o E2E entre servicos (etapa E1.16).

# Por que isto existe

O `MotorE2ETest` (E1.15) cobre o motor com o worker substituido por WireMock.
Sobra uma lacuna: **nenhum teste faz o Java e o Python conversarem de verdade**.
Um erro de contrato — nome de campo, formato de data, tratamento de nulo — passa
pelos dois lados sem ser detectado, porque cada um e testado com o outro
simulado por mim.

Aqui os dois processos sao reais. So as **fontes externas** sao falsas: sem
rede, sem cota, sem preco que muda entre uma execucao e outra.

# Por que a rota escolhe o cenario

O core-java nao pode ganhar um parametro "modo de teste" — isso seria codigo de
producao existindo por causa de teste, exatamente o que a secao 3 do
PLANO-DE-ACAO proibe. Mas ele **ja** manda origem e destino.

Entao o destino vira o seletor: cada codigo IATA reservado corresponde a um
desfecho. O core continua sem saber que existe teste; do lado dele e apenas
outra rota.

Os codigos escolhidos comecam com `ZZ` — faixa que a IATA nao atribui, entao
nenhum deles pode colidir com um aeroporto real que alguem queira monitorar.

# Precos fixos, nao aleatorios

Os valores abaixo sao constantes e estao repetidos nas assercoes do
`E2EServicosTest`. Preco derivado de hash seria "mais realista" e obrigaria o
teste a recalcular a mesma formula — o teste passaria a conferir a si mesmo.
Numero fixo em cima da mesa e conferivel a olho.
"""

import logging
from datetime import date, datetime, timedelta
from decimal import Decimal

from app.control.busca.portas import ProviderError
from app.schemas import (
    CalendarSearchRequest,
    CalendarSearchResponse,
    ConfirmedOffer,
    ConfirmRequest,
    FlightOffer,
)

logger = logging.getLogger(__name__)

FONTE_CAMADA_1 = "TRAVELPAYOUTS"
FONTE_CAMADA_2 = "FAST_FLIGHTS"

# ---------------------------------------------------------------- cenarios

#: Camada 1 devolve estas duas ofertas no caminho normal. A primeira e a
#: barata: e ela que vira candidata a confirmacao.
PRECO_CACHE_BARATO = Decimal("3480.00")
PRECO_CACHE_CARO = Decimal("4900.00")

#: Preco que a camada 2 confirma no caminho normal. **Acima** do cache de
#: proposito: e assim na realidade, e obriga o teste a provar que o alerta
#: carrega o preco confirmado, e nao o do cache.
PRECO_CONFIRMADO = Decimal("3720.00")

#: Preco confirmado no cenario do candidato ilusorio. Alto o bastante para
#: estourar qualquer teto que o teste configure.
PRECO_CONFIRMADO_ABSURDO = Decimal("9990.00")

NORMAL = "normal"
CONFIRMA_ACIMA_DO_CACHE = "confirma-acima-do-cache"
VOO_NAO_EXISTE = "voo-nao-existe"
CAMADA_2_FORA = "camada-2-fora"
CAMADA_1_FORA = "camada-1-fora"
JANELA_VAZIA = "janela-vazia"

#: Destino reservado -> desfecho. Qualquer outro destino segue o caminho normal.
CENARIOS: dict[str, str] = {
    "ZZA": CONFIRMA_ACIMA_DO_CACHE,
    "ZZB": CAMADA_2_FORA,
    "ZZC": VOO_NAO_EXISTE,
    "ZZD": CAMADA_1_FORA,
    "ZZE": JANELA_VAZIA,
}


def cenario_de(destino: str) -> str:
    return CENARIOS.get(destino.upper(), NORMAL)


# ------------------------------------------------------------------ camada 1


class FakeCalendarProvider:
    """Camada 1 falsa: devolve ofertas dentro da janela pedida, sempre iguais."""

    name = "fake-calendar"

    async def buscar(self, req: CalendarSearchRequest) -> CalendarSearchResponse:
        req.validar_janela()
        cenario = cenario_de(req.destination)

        if cenario == CAMADA_1_FORA:
            # Mesma excecao que o Travelpayouts levanta: o roteador a traduz em
            # 502, e o core marca a busca como FAILED.
            raise ProviderError(self.name, "fonte de varredura indisponivel (cenario de teste)")

        if cenario == JANELA_VAZIA:
            # A fonte respondeu, e nada sobreviveu ao filtro. Distinguir isto de
            # "a fonte morreu" e o motivo de `returned` e `kept` existirem.
            return CalendarSearchResponse(
                origin=req.origin,
                destination=req.destination,
                offers=[],
                returned=30,
                kept=0,
                provider_origin=req.origin,
                provider_destination=req.destination,
                warnings=["30 ofertas descartadas por estarem fora dos criterios pedidos"],
            )

        ida = req.departure_from
        volta = self._volta(req, ida)

        ofertas = [
            self._oferta(ida, volta, PRECO_CACHE_BARATO, req.currency, "IB", "6026", 1),
            self._oferta(
                self._segunda_data(req),
                self._volta(req, self._segunda_data(req)),
                PRECO_CACHE_CARO,
                req.currency,
                "AF",
                "0454",
                2,
            ),
        ]

        return CalendarSearchResponse(
            origin=req.origin,
            destination=req.destination,
            offers=ofertas,
            returned=30,
            kept=len(ofertas),
            provider_origin=req.origin,
            provider_destination=req.destination,
            warnings=[],
        )

    def _segunda_data(self, req: CalendarSearchRequest) -> date:
        """Uma segunda data dentro da janela, sem nunca ultrapassa-la (RISCO-007)."""
        return min(req.departure_from + timedelta(days=3), req.departure_to)

    def _volta(self, req: CalendarSearchRequest, ida: date) -> date | None:
        if req.return_from is not None:
            return max(req.return_from, ida + timedelta(days=1))
        return ida + timedelta(days=12)

    def _oferta(
        self,
        ida: date,
        volta: date | None,
        preco: Decimal,
        moeda: str,
        cia: str,
        voo: str,
        escalas: int,
    ) -> FlightOffer:
        return FlightOffer(
            departure_date=ida,
            return_date=volta,
            price=preco,
            currency=moeda,
            airline=cia,
            flight_number=voo,
            stops=escalas,
            departure_at=datetime.combine(ida, datetime.min.time()).replace(hour=21, minute=40),
            arrival_at=(
                datetime.combine(volta, datetime.min.time()).replace(hour=12, minute=5)
                if volta
                else None
            ),
            expires_at=None,
            source=FONTE_CAMADA_1,
        )


# ------------------------------------------------------------------ camada 2


class FakeConfirmationProvider:
    """Camada 2 falsa: confirma, nega ou cai, conforme o destino."""

    name = "fake-confirmation"

    async def confirm(self, req: ConfirmRequest) -> ConfirmedOffer | None:
        cenario = cenario_de(req.destination)

        if cenario == CAMADA_2_FORA:
            # ProviderError e o que a cadeia entende como "nao consegui
            # consultar" — vira degraded=true, nao "voo nao existe".
            raise ProviderError(self.name, "verificacao ao vivo indisponivel (cenario de teste)")

        if cenario == VOO_NAO_EXISTE:
            # None e diferente de excecao: consultamos, e nao ha voo assim.
            return None

        preco = (
            PRECO_CONFIRMADO_ABSURDO
            if cenario == CONFIRMA_ACIMA_DO_CACHE
            else PRECO_CONFIRMADO
        )

        return ConfirmedOffer(
            departure_date=req.departure_date,
            return_date=req.return_date,
            price=preco,
            currency=req.currency,
            airline="Iberia",
            airline_code="IB",
            stops=1,
            duration_minutes=745,
            departure_at=datetime.combine(req.departure_date, datetime.min.time()).replace(
                hour=21, minute=40
            ),
            arrival_at=(
                datetime.combine(req.return_date, datetime.min.time()).replace(hour=12, minute=5)
                if req.return_date
                else None
            ),
            # Aeroportos reais, que a camada 1 nao tem (RISCO-006).
            departure_airport=req.origin,
            arrival_airport=req.destination,
            source=FONTE_CAMADA_2,
        )
