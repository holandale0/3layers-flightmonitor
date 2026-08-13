"""Testes da camada 1.

Sem rede: o HTTP e mockado com httpx2.MockTransport. As respostas imitam o
formato real observado na API, incluindo as duas armadilhas confirmadas
(RISCO-006 e RISCO-007).
"""

from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

import httpx2
import pytest

from app.boundary.gateway.travelpayouts import (
    TravelpayoutsError,
    TravelpayoutsProvider,
    _meses_da_janela,
)
from app.schemas import CalendarSearchRequest


def cliente_falso(handler) -> httpx2.AsyncClient:
    return httpx2.AsyncClient(transport=httpx2.MockTransport(handler))


def resposta_json(payload: dict, status: int = 200):
    def handler(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(status, json=payload)

    return handler


def oferta_bruta(
    origem="SAO",
    destino="LIS",
    preco=2980,
    transfers=0,
    partida="2027-03-12T22:30:00-03:00",
    volta="2027-03-27T10:15:00+01:00",
    expira=None,
):
    if expira is None:
        expira = (datetime.now(UTC) + timedelta(hours=6)).isoformat().replace("+00:00", "Z")
    return {
        "origin": origem,
        "destination": destino,
        "airline": "TP",
        "departure_at": partida,
        "return_at": volta,
        "expires_at": expira,
        "price": preco,
        "flight_number": 1234,
        "transfers": transfers,
    }


def pedido(**extra) -> CalendarSearchRequest:
    base = {
        "origin": "GRU",
        "destination": "LIS",
        "departure_from": date(2027, 3, 10),
        "departure_to": date(2027, 3, 20),
    }
    base.update(extra)
    return CalendarSearchRequest(**base)


# --------------------------------------------------------------- utilitarios

def test_janela_dentro_de_um_mes_gera_uma_consulta():
    assert _meses_da_janela(date(2027, 3, 10), date(2027, 3, 20)) == ["2027-03"]


def test_janela_que_cruza_a_virada_do_mes_gera_duas_consultas():
    assert _meses_da_janela(date(2027, 3, 28), date(2027, 4, 5)) == ["2027-03", "2027-04"]


def test_janela_que_cruza_a_virada_do_ano():
    assert _meses_da_janela(date(2026, 12, 20), date(2027, 1, 10)) == ["2026-12", "2027-01"]


# ------------------------------------------------------------------- sucesso

@pytest.mark.anyio
async def test_oferta_dentro_da_janela_e_mantida():
    payload = {"success": True, "data": {"2027-03-12": oferta_bruta()}}
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert r.kept == 1
    oferta = r.offers[0]
    assert oferta.departure_date == date(2027, 3, 12)
    assert oferta.price == Decimal("2980")
    assert oferta.airline == "TP"
    assert oferta.stops == 0
    assert oferta.source == "TRAVELPAYOUTS"


@pytest.mark.anyio
async def test_rota_gravada_e_a_pedida_e_nao_a_devolvida():
    """RISCO-006: pedimos GRU, a fonte responde SAO. O historico usa GRU."""
    payload = {"success": True, "data": {"2027-03-12": oferta_bruta(origem="SAO")}}
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert r.origin == "GRU"
    assert r.provider_origin == "SAO"
    assert any("SAO" in a for a in r.warnings)


@pytest.mark.anyio
async def test_datas_fora_do_mes_pedido_sao_descartadas():
    """RISCO-007: a fonte ignora o mes pedido e devolve o que tem em cache."""
    payload = {
        "success": True,
        "data": {
            "2026-08-14": oferta_bruta(partida="2026-08-14T21:45:00-03:00", volta=None),
            "2026-09-11": oferta_bruta(partida="2026-09-11T20:45:00-03:00", volta=None),
            "2027-03-12": oferta_bruta(),
        },
    }
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert r.returned == 3
    assert r.kept == 1
    assert r.offers[0].departure_date == date(2027, 3, 12)
    assert any("fora dos criterios" in a for a in r.warnings)


@pytest.mark.anyio
async def test_preco_ja_vencido_e_descartado():
    """RISCO-003: preco cacheado vencido nao serve nem como candidato."""
    vencido = (datetime.now(UTC) - timedelta(hours=2)).isoformat().replace("+00:00", "Z")
    payload = {"success": True, "data": {"2027-03-12": oferta_bruta(expira=vencido)}}
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert r.kept == 0
    assert any("vencido" in a for a in r.warnings)


@pytest.mark.anyio
async def test_filtra_por_numero_de_escalas():
    payload = {
        "success": True,
        "data": {
            "2027-03-12": oferta_bruta(transfers=0),
            "2027-03-13": oferta_bruta(transfers=2, partida="2027-03-13T22:30:00-03:00"),
        },
    }
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(max_stops=1), cliente=cliente_falso(resposta_json(payload)))

    assert r.kept == 1
    assert r.offers[0].stops == 0


@pytest.mark.anyio
async def test_filtra_pela_janela_de_volta():
    payload = {
        "success": True,
        "data": {
            "2027-03-12": oferta_bruta(volta="2027-03-27T10:15:00+01:00"),
            "2027-03-13": oferta_bruta(
                partida="2027-03-13T22:30:00-03:00", volta="2027-05-30T10:15:00+01:00"
            ),
        },
    }
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(
        pedido(return_from=date(2027, 3, 20), return_to=date(2027, 4, 5)),
        cliente=cliente_falso(resposta_json(payload)),
    )

    assert r.kept == 1
    assert r.offers[0].return_date == date(2027, 3, 27)


@pytest.mark.anyio
async def test_ofertas_saem_ordenadas_pelo_menor_preco():
    payload = {
        "success": True,
        "data": {
            "2027-03-12": oferta_bruta(preco=3500),
            "2027-03-13": oferta_bruta(preco=2100, partida="2027-03-13T22:30:00-03:00"),
            "2027-03-14": oferta_bruta(preco=2900, partida="2027-03-14T22:30:00-03:00"),
        },
    }
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert [o.price for o in r.offers] == [Decimal("2100"), Decimal("2900"), Decimal("3500")]


@pytest.mark.anyio
async def test_horario_de_voo_vem_sem_fuso():
    """Partida e horario local do aeroporto: o offset e descartado, nao convertido."""
    payload = {"success": True, "data": {"2027-03-12": oferta_bruta()}}
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    partida = r.offers[0].departure_at
    assert partida.tzinfo is None
    assert partida.hour == 22 and partida.minute == 30


# --------------------------------------------------------------------- falhas

@pytest.mark.anyio
async def test_token_ausente_falha_cedo():
    provider = TravelpayoutsProvider("")
    with pytest.raises(TravelpayoutsError, match="nao configurado"):
        await provider.buscar(pedido())


@pytest.mark.anyio
async def test_erro_http_vira_erro_de_provider():
    provider = TravelpayoutsProvider("token-de-teste")
    with pytest.raises(TravelpayoutsError, match="HTTP 500"):
        await provider.buscar(pedido(), cliente=cliente_falso(resposta_json({}, status=500)))


@pytest.mark.anyio
async def test_resposta_de_recusa_vira_erro_de_provider():
    payload = {"success": False, "error": "Invalid token"}
    provider = TravelpayoutsProvider("token-de-teste")
    with pytest.raises(TravelpayoutsError, match="recusou"):
        await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))


@pytest.mark.anyio
async def test_timeout_vira_erro_de_provider():
    def handler(request: httpx2.Request):
        raise httpx2.TimeoutException("demorou demais", request=request)

    provider = TravelpayoutsProvider("token-de-teste")
    with pytest.raises(TravelpayoutsError, match="timeout"):
        await provider.buscar(pedido(), cliente=cliente_falso(handler))


@pytest.mark.anyio
async def test_registro_malformado_nao_derruba_a_busca():
    """Um dia com dado corrompido nao pode invalidar o mes inteiro."""
    payload = {
        "success": True,
        "data": {
            "data-invalida": oferta_bruta(),
            "2027-03-12": {"price": "nao-e-numero"},
            "2027-03-13": oferta_bruta(partida="2027-03-13T22:30:00-03:00"),
        },
    }
    provider = TravelpayoutsProvider("token-de-teste")

    r = await provider.buscar(pedido(), cliente=cliente_falso(resposta_json(payload)))

    assert r.kept == 1
    assert r.offers[0].departure_date == date(2027, 3, 13)


@pytest.mark.anyio
async def test_janela_invertida_e_recusada():
    provider = TravelpayoutsProvider("token-de-teste")
    req = pedido(departure_from=date(2027, 3, 20), departure_to=date(2027, 3, 10))

    with pytest.raises(ValueError, match="departure_to"):
        await provider.buscar(req)
