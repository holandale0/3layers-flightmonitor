"""O canario, verificado contra as quebras que ja aconteceram de verdade.

Um canario que nao pega as falhas conhecidas nao protege de nada. Os testes
abaixo reproduzem, uma a uma, as tres quebras reais da camada 2 documentadas no
RISCO-002 — e exigem que a sonda as acuse.
"""

from datetime import date, timedelta
from decimal import Decimal

import pytest

from app.control.busca.cadeia import ConfirmationChain
from app.control.canario.sonda import sondar_camada1, sondar_camada2
from app.schemas import (
    CalendarSearchResponse,
    ConfirmedOffer,
    ConfirmResponse,
    FlightOffer,
)

PARTIDA = date.today() + timedelta(days=30)


def oferta_boa(**trocas) -> FlightOffer:
    campos = {
        "departure_date": PARTIDA,
        "price": Decimal("980.00"),
        "currency": "BRL",
        "airline": "LATAM",
        "stops": 0,
        "source": "TRAVELPAYOUTS",
    }
    campos.update(trocas)
    return FlightOffer.model_construct(**campos)


class CalendarFalso:
    """Camada 1 controlada: devolve exatamente o que o teste mandar."""

    name = "sonda-teste"

    def __init__(self, offers=None, erro=None):
        self._offers = offers if offers is not None else [oferta_boa()]
        self._erro = erro

    async def buscar(self, req) -> CalendarSearchResponse:
        if self._erro:
            raise self._erro
        return CalendarSearchResponse.model_construct(
            origin=req.origin, destination=req.destination,
            offers=self._offers, returned=len(self._offers), kept=len(self._offers))


class ConfirmacaoFalsa:
    name = "confirmacao-teste"

    def __init__(self, resposta=None, erro=None):
        self._resposta = resposta
        self._erro = erro

    async def confirm(self, req) -> ConfirmResponse:
        if self._erro:
            raise self._erro
        return self._resposta


def cadeia(resposta=None, erro=None) -> ConfirmationChain:
    """Uma cadeia cujo `confirm` devolve o que o teste quiser."""
    c = ConfirmationChain([])
    falsa = ConfirmacaoFalsa(resposta, erro)
    c.confirm = falsa.confirm  # type: ignore[method-assign]
    return c


# ---------------------------------------------------------------- caminho bom


@pytest.mark.anyio
async def test_fonte_saudavel_passa_no_canario():
    resultado = await sondar_camada1(CalendarFalso())

    assert resultado.respondeu
    assert resultado.formato_ok
    assert resultado.achados == []


# ------------------------------------------- as tres quebras reais do RISCO-002


@pytest.mark.anyio
async def test_pega_tipo_trocado_no_campo_airline():
    """Quebra real: `list[Airline]` anotado, `list[str]` em execucao.

    O tipo mudou e o HTTP continuou 200 — nenhum teste de disponibilidade
    perceberia.
    """
    resultado = await sondar_camada1(CalendarFalso([oferta_boa(airline=["LATAM"])]))

    assert not resultado.formato_ok
    assert any("airline" in str(a) for a in resultado.achados)


@pytest.mark.anyio
async def test_pega_campo_obrigatorio_nulo():
    """Quebra real: o parser devolveu `time=[None, 45]`, com a hora ausente.

    Aqui o equivalente e a data de partida vindo nula: o campo existe, mas o
    valor nao serve.
    """
    resultado = await sondar_camada1(CalendarFalso([oferta_boa(departure_date=None)]))

    assert not resultado.formato_ok
    assert any("departure_date" in str(a) for a in resultado.achados)


@pytest.mark.anyio
async def test_pega_api_da_biblioteca_que_mudou():
    """Quebra real: a API mudou por completo entre 2.x e 3.x.

    O sintoma e uma excecao na chamada. O canario reporta em vez de propagar —
    ele existe para contar a ma noticia, e nao para virar mais uma.
    """
    resultado = await sondar_camada1(
        CalendarFalso(erro=TypeError("buscar() got an unexpected keyword argument")))

    assert not resultado.respondeu
    assert not resultado.formato_ok
    assert "TypeError" in resultado.erro


# ------------------------------------------------------ outros sinais de forma


@pytest.mark.anyio
async def test_preco_como_texto_e_sinal():
    # Preco em string atravessa JSON sem reclamar e quebra toda a comparacao
    # com o teto — uma das falhas mais silenciosas possiveis.
    resultado = await sondar_camada1(CalendarFalso([oferta_boa(price="980.00")]))

    assert not resultado.formato_ok
    assert any("price" in str(a) for a in resultado.achados)


@pytest.mark.anyio
async def test_rota_movimentada_sem_nenhuma_oferta_e_sinal():
    # GRU-GIG por sete dias sem UM preco nao e ausencia de promocao.
    resultado = await sondar_camada1(CalendarFalso(offers=[]))

    assert resultado.respondeu
    assert not resultado.formato_ok
    assert any("offers" in str(a) for a in resultado.achados)


@pytest.mark.anyio
async def test_camada2_degradada_e_o_estado_do_bug014():
    """`degraded` e exatamente o que o BUG-014 produziu por seis semanas."""
    resposta = ConfirmResponse.model_construct(
        confirmed=False, degraded=True,
        warnings=["nenhuma fonte de confirmacao habilitada"], attempts=[])

    resultado = await sondar_camada2(cadeia(resposta))

    assert not resultado.respondeu
    assert not resultado.formato_ok
    assert "nenhuma fonte" in resultado.erro


@pytest.mark.anyio
async def test_camada2_confirmando_passa():
    resposta = ConfirmResponse.model_construct(
        confirmed=True, degraded=False, warnings=[], attempts=[],
        offer=ConfirmedOffer.model_construct(
            departure_date=PARTIDA, price=Decimal("1200.00"),
            currency="BRL", airline="GOL", stops=0))

    resultado = await sondar_camada2(cadeia(resposta))

    assert resultado.formato_ok
    assert resultado.ofertas == 1


@pytest.mark.anyio
async def test_canario_nunca_propaga_excecao():
    """O contrato que torna o canario seguro de agendar.

    Se ele lancasse, uma fonte instavel derrubaria a rotina que existe
    justamente para observar fontes instaveis.
    """
    resultado = await sondar_camada2(cadeia(erro=RuntimeError("conexao caiu")))

    assert resultado.erro is not None
    assert not resultado.formato_ok
