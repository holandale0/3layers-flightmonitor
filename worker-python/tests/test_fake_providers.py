"""Testes das fontes falsas usadas no E2E entre servicos (etapa E1.16).

Testar um dublê parece redundante, e nao e: o `E2EServicosTest` do lado Java vai
afirmar precos e datas exatos vindos daqui. Se este modulo mudar de
comportamento sem aviso, aquele teste falha do outro lado da fronteira, em
outra linguagem, com uma mensagem que nao aponta para a causa.

Estes testes sao o alarme perto do vazamento.
"""

from datetime import date, timedelta
from decimal import Decimal

import pytest

from app.boundary.gateway.fake import (
    PRECO_CACHE_BARATO,
    PRECO_CACHE_CARO,
    PRECO_CONFIRMADO,
    PRECO_CONFIRMADO_ABSURDO,
    FakeCalendarProvider,
    FakeConfirmationProvider,
)
from app.boundary.gateway.fastflights import FastFlightsProvider
from app.boundary.gateway.travelpayouts import TravelpayoutsProvider
from app.composicao.busca import build_calendar_provider, build_confirmation_chain
from app.config import Settings
from app.control.busca.cadeia import ConfirmationChain
from app.control.busca.portas import ProviderError
from app.schemas import CalendarSearchRequest, ConfirmRequest

IDA = date.today() + timedelta(days=60)
VOLTA = IDA + timedelta(days=12)


def varredura(destino: str = "LIS", **kwargs) -> CalendarSearchRequest:
    dados = {
        "origin": "GRU",
        "destination": destino,
        "departure_from": IDA,
        "departure_to": IDA + timedelta(days=10),
        "currency": "BRL",
    }
    dados.update(kwargs)
    return CalendarSearchRequest(**dados)


def confirmacao(destino: str = "LIS") -> ConfirmRequest:
    return ConfirmRequest(
        origin="GRU",
        destination=destino,
        departure_date=IDA,
        return_date=VOLTA,
        currency="BRL",
        passengers=1,
        candidate_price=PRECO_CACHE_BARATO,
    )


# ------------------------------------------------------------------ camada 1


@pytest.mark.anyio
async def test_varredura_normal_devolve_duas_ofertas_na_janela():
    r = await FakeCalendarProvider().buscar(varredura())

    assert r.kept == 2
    assert r.returned == 30
    assert [o.price for o in r.offers] == [PRECO_CACHE_BARATO, PRECO_CACHE_CARO]
    # RISCO-007: nenhuma oferta pode cair fora da janela pedida. E o erro que a
    # fonte real ja cometeu, entao a falsa nao pode escondê-lo.
    for o in r.offers:
        assert varredura().departure_from <= o.departure_date <= varredura().departure_to


@pytest.mark.anyio
async def test_varredura_e_deterministica():
    """Duas chamadas iguais devolvem exatamente a mesma coisa.

    E o que separa um dublê de uma fonte de verdade — e o que permite ao teste
    Java afirmar `3720.00` em vez de `> 0`.
    """
    primeira = await FakeCalendarProvider().buscar(varredura())
    segunda = await FakeCalendarProvider().buscar(varredura())

    assert primeira.model_dump() == segunda.model_dump()


@pytest.mark.anyio
async def test_janela_de_um_dia_nao_gera_data_fora():
    um_dia = varredura(departure_to=IDA)

    r = await FakeCalendarProvider().buscar(um_dia)

    assert {o.departure_date for o in r.offers} == {IDA}


@pytest.mark.anyio
async def test_cenario_de_fonte_fora_do_ar_levanta():
    with pytest.raises(ProviderError, match="indisponivel"):
        await FakeCalendarProvider().buscar(varredura("ZZD"))


@pytest.mark.anyio
async def test_cenario_de_janela_vazia_responde_sem_ofertas():
    r = await FakeCalendarProvider().buscar(varredura("ZZE"))

    # A distincao que importa: a fonte respondeu (returned > 0) e nada passou no
    # filtro. Nao e o mesmo que a fonte ter morrido.
    assert r.offers == []
    assert r.returned == 30
    assert r.kept == 0


@pytest.mark.anyio
async def test_janela_incoerente_continua_sendo_erro_do_chamador():
    ruim = varredura()
    ruim.departure_to = ruim.departure_from - timedelta(days=1)

    with pytest.raises(ValueError, match="departure_to"):
        await FakeCalendarProvider().buscar(ruim)


# ------------------------------------------------------------------ camada 2


@pytest.mark.anyio
async def test_confirmacao_normal_devolve_preco_acima_do_cache():
    oferta = await FakeConfirmationProvider().confirm(confirmacao())

    assert oferta is not None
    assert oferta.price == PRECO_CONFIRMADO
    # Acima do cache de proposito: e o que obriga o teste Java a provar que o
    # alerta carrega o preco confirmado, e nao o do cache.
    assert oferta.price > PRECO_CACHE_BARATO
    assert oferta.source == "FAST_FLIGHTS"
    assert oferta.departure_airport == "GRU"


@pytest.mark.anyio
async def test_cenario_de_candidato_ilusorio_confirma_preco_absurdo():
    oferta = await FakeConfirmationProvider().confirm(confirmacao("ZZA"))

    assert oferta is not None
    assert oferta.price == PRECO_CONFIRMADO_ABSURDO


@pytest.mark.anyio
async def test_voo_inexistente_devolve_none_e_nao_excecao():
    """`None` e excecao significam coisas diferentes, e a cadeia depende disso."""
    assert await FakeConfirmationProvider().confirm(confirmacao("ZZC")) is None


@pytest.mark.anyio
async def test_camada_2_fora_do_ar_levanta_provider_error():
    with pytest.raises(ProviderError):
        await FakeConfirmationProvider().confirm(confirmacao("ZZB"))


@pytest.mark.anyio
async def test_cadeia_traduz_os_tres_desfechos():
    """Os falsos precisam produzir os tres desfechos que o core sabe distinguir."""
    cadeia = ConfirmationChain([FakeConfirmationProvider()])

    normal = await cadeia.confirm(confirmacao())
    assert (normal.confirmed, normal.degraded) == (True, False)

    inexistente = await cadeia.confirm(confirmacao("ZZC"))
    assert (inexistente.confirmed, inexistente.degraded) == (False, False)

    caida = await cadeia.confirm(confirmacao("ZZB"))
    assert (caida.confirmed, caida.degraded) == (False, True)


@pytest.mark.anyio
async def test_divergencia_grande_vira_aviso():
    """O aviso de divergencia entre cache e realidade tem que continuar saindo."""
    cadeia = ConfirmationChain([FakeConfirmationProvider()])

    r = await cadeia.confirm(confirmacao("ZZA"))

    assert r.confirmed
    assert r.warnings, "divergencia de 187% precisa gerar aviso"


# -------------------------------------------------------------------- fabrica


def test_fabrica_so_entrega_falsos_quando_a_chave_esta_ligada():
    real = Settings(use_fake_providers=False, travelpayouts_token="x")
    falso = Settings(use_fake_providers=True, travelpayouts_token="x")

    assert isinstance(build_calendar_provider(real), TravelpayoutsProvider)
    assert isinstance(build_calendar_provider(falso), FakeCalendarProvider)

    assert build_confirmation_chain(real).provider_names == [FastFlightsProvider.name]
    assert build_confirmation_chain(falso).provider_names == [FakeConfirmationProvider.name]


def test_o_padrao_e_desligado():
    """Um `true` esquecido faria o sistema inventar precos em silencio."""
    assert Settings(travelpayouts_token="x").use_fake_providers is False


def test_falsos_ignoram_a_chave_do_fastflights():
    """Com os falsos ligados, desligar o fast-flights nao pode cegar a camada 2.

    Se a ordem das checagens fosse invertida, um `.env` com
    FASTFLIGHTS_ENABLED=false deixaria o E2E rodando com a cadeia vazia — e
    todo cenario viraria "degradado", passando por engano.
    """
    s = Settings(use_fake_providers=True, fastflights_enabled=False, travelpayouts_token="x")

    assert build_confirmation_chain(s).provider_names == [FakeConfirmationProvider.name]


def test_precos_do_cenario_normal_sao_os_que_o_teste_java_afirma():
    """Guarda-chuva contra mudanca silenciosa dos numeros combinados.

    Estes tres valores estao escritos tambem no E2EServicosTest, do outro lado
    da fronteira. Mudar um sem o outro quebra um teste em Java com mensagem que
    nao aponta para ca.
    """
    assert PRECO_CACHE_BARATO == Decimal("3480.00")
    assert PRECO_CACHE_CARO == Decimal("4900.00")
    assert PRECO_CONFIRMADO == Decimal("3720.00")
    assert PRECO_CONFIRMADO_ABSURDO == Decimal("9990.00")
