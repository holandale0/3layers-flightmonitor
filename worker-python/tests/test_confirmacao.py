"""Testes da camada 2 e da cadeia de confirmacao.

A enfase esta nos CAMINHOS DE FALHA, de proposito: a camada 2 depende do
formato interno do Google e vai quebrar. O que precisa estar garantido nao e
que ela funcione, e que a quebra dela nao derrube o sistema.
"""

from datetime import date
from decimal import Decimal

import pytest

from app.boundary.gateway.fastflights import FastFlightsProvider, _dividir_ida_volta, _instante
from app.control.busca.cadeia import ConfirmationChain
from app.control.busca.portas import ProviderError
from app.schemas import ConfirmedOffer, ConfirmRequest


def pedido(**extra) -> ConfirmRequest:
    base = {
        "origin": "GRU",
        "destination": "LIS",
        "departure_date": date(2027, 3, 12),
        "return_date": date(2027, 3, 27),
    }
    base.update(extra)
    return ConfirmRequest(**base)


def oferta(preco="2980", **extra) -> ConfirmedOffer:
    base = {
        "departure_date": date(2027, 3, 12),
        "price": Decimal(preco),
        "currency": "BRL",
        "source": "FAST_FLIGHTS",
    }
    base.update(extra)
    return ConfirmedOffer(**base)


class ProviderFalso:
    """Dublê configuravel: confirma, nao encontra, ou falha."""

    def __init__(self, name: str, resultado=None, erro: str | None = None, explode=False):
        self.name = name
        self._resultado = resultado
        self._erro = erro
        self._explode = explode
        self.chamadas = 0

    async def confirm(self, req: ConfirmRequest):
        self.chamadas += 1
        if self._explode:
            raise RuntimeError("provider mal comportado")
        if self._erro:
            raise ProviderError(self.name, self._erro)
        return self._resultado


# ------------------------------------------------------------------- helpers

class PernaFalsa:
    def __init__(self, de: str, para: str, duracao: int = 600):
        self.from_airport = type("A", (), {"code": de})()
        self.to_airport = type("A", (), {"code": para})()
        self.duration = duracao


def test_divide_ida_e_volta_em_voo_direto():
    pernas = [PernaFalsa("GRU", "LIS"), PernaFalsa("LIS", "GRU")]
    ida, volta = _dividir_ida_volta(pernas, "LIS")
    assert len(ida) == 1 and len(volta) == 1


def test_divide_ida_e_volta_com_conexao():
    """Sem separar direcao, um GRU->LIS->GRU com escala pareceria ter 3 escalas."""
    pernas = [
        PernaFalsa("GRU", "CDG"),
        PernaFalsa("CDG", "LIS"),
        PernaFalsa("LIS", "GRU"),
    ]
    ida, volta = _dividir_ida_volta(pernas, "LIS")
    assert len(ida) == 2  # 1 escala na ida
    assert len(volta) == 1


def test_instante_invalido_devolve_none_em_vez_de_estourar():
    assert _instante(None) is None
    assert _instante(type("X", (), {"date": "lixo", "time": None})()) is None


# ------------------------------------------------------- cadeia: caminho feliz

@pytest.mark.anyio
async def test_primeiro_provider_que_confirma_encerra_a_cadeia():
    primeiro = ProviderFalso("primeiro", resultado=oferta())
    segundo = ProviderFalso("segundo", resultado=oferta())
    cadeia = ConfirmationChain([primeiro, segundo])

    r = await cadeia.confirm(pedido())

    assert r.confirmed is True
    assert r.provider == "primeiro"
    assert segundo.chamadas == 0  # nao gastou a segunda fonte a toa


@pytest.mark.anyio
async def test_falha_do_primeiro_cai_para_o_segundo():
    primeiro = ProviderFalso("primeiro", erro="o Google mudou o HTML")
    segundo = ProviderFalso("segundo", resultado=oferta())
    cadeia = ConfirmationChain([primeiro, segundo])

    r = await cadeia.confirm(pedido())

    assert r.confirmed is True
    assert r.provider == "segundo"
    assert r.degraded is False
    assert [a.provider for a in r.attempts] == ["primeiro", "segundo"]
    assert r.attempts[0].ok is False
    assert "mudou o HTML" in r.attempts[0].error


# ------------------------------------------------------- cadeia: degradacao

@pytest.mark.anyio
async def test_todas_as_fontes_falhando_degrada_em_vez_de_estourar():
    """O comportamento mais importante do sistema inteiro."""
    cadeia = ConfirmationChain([
        ProviderFalso("um", erro="timeout"),
        ProviderFalso("dois", erro="HTTP 429"),
    ])

    r = await cadeia.confirm(pedido())

    assert r.degraded is True
    assert r.confirmed is False
    assert r.offer is None
    assert len(r.attempts) == 2
    assert all(a.ok is False for a in r.attempts)
    assert any("seguindo sem detalhe de voo" in a for a in r.warnings)


@pytest.mark.anyio
async def test_cadeia_vazia_degrada_sem_erro():
    r = await ConfirmationChain([]).confirm(pedido())

    assert r.degraded is True
    assert r.confirmed is False
    assert any("nenhuma fonte" in a for a in r.warnings)


@pytest.mark.anyio
async def test_excecao_inesperada_do_provider_nao_derruba_a_cadeia():
    """Um provider mal comportado nao pode quebrar a varredura."""
    cadeia = ConfirmationChain([
        ProviderFalso("bugado", explode=True),
        ProviderFalso("bom", resultado=oferta()),
    ])

    r = await cadeia.confirm(pedido())

    assert r.confirmed is True
    assert r.provider == "bom"
    assert "excecao nao tratada" in r.attempts[0].error


# ------------------------------------------- cadeia: candidato nao se sustenta

@pytest.mark.anyio
async def test_voo_inexistente_e_resposta_definitiva_e_nao_falha():
    """Consultar com sucesso e nao achar voo != nao conseguir consultar."""
    segundo = ProviderFalso("segundo", resultado=oferta())
    cadeia = ConfirmationChain([ProviderFalso("primeiro", resultado=None), segundo])

    r = await cadeia.confirm(pedido())

    assert r.confirmed is False
    assert r.degraded is False  # nao e degradacao: e resposta
    assert segundo.chamadas == 0  # nao adianta perguntar de novo
    assert any("nao se sustentou" in a for a in r.warnings)


# --------------------------------------------------- divergencia de preco

@pytest.mark.anyio
async def test_avisa_quando_preco_real_diverge_do_candidato():
    """Mede o falso-positivo do cache da camada 1 (RISCO-003)."""
    cadeia = ConfirmationChain([ProviderFalso("p", resultado=oferta(preco="3500"))])

    r = await cadeia.confirm(pedido(candidate_price=Decimal("2980")))

    assert r.confirmed is True
    assert any("acima do candidato" in a for a in r.warnings)


@pytest.mark.anyio
async def test_divergencia_pequena_nao_gera_ruido():
    cadeia = ConfirmationChain([ProviderFalso("p", resultado=oferta(preco="3000"))])

    r = await cadeia.confirm(pedido(candidate_price=Decimal("2980")))

    assert not any("candidato" in a for a in r.warnings)


@pytest.mark.anyio
async def test_avisa_quando_o_aeroporto_real_difere_do_pedido():
    """A camada 1 trabalha em nivel de cidade (RISCO-006); a 2 revela o aeroporto."""
    cadeia = ConfirmationChain([
        ProviderFalso("p", resultado=oferta(departure_airport="VCP"))
    ])

    r = await cadeia.confirm(pedido())

    assert any("parte de VCP" in a for a in r.warnings)


# ------------------------------------------------ adaptador do fast-flights

@pytest.mark.anyio
async def test_biblioteca_quebrada_vira_provider_error(monkeypatch):
    """O modo de falha mais provavel: o Google muda e o parser estoura."""
    provider = FastFlightsProvider()

    def explodir(req):
        raise AttributeError("'NoneType' object has no attribute 'text'")

    monkeypatch.setattr(provider, "_consultar", explodir)

    with pytest.raises(AttributeError):
        # Sem a cadeia, a excecao sobe — e por isso o provider nunca e usado sozinho.
        await provider.confirm(pedido())


@pytest.mark.anyio
async def test_erro_do_adaptador_e_absorvido_pela_cadeia(monkeypatch):
    """Prova a protecao real: com a cadeia, a mesma quebra vira degradacao."""
    provider = FastFlightsProvider()

    async def falhar(req):
        raise ProviderError("fast-flights", "AttributeError: parser quebrou")

    monkeypatch.setattr(provider, "confirm", falhar)

    r = await ConfirmationChain([provider]).confirm(pedido())

    assert r.degraded is True
    assert r.confirmed is False
    assert "parser quebrou" in r.attempts[0].error


def test_escalas_acima_do_limite_descartam_a_oferta():
    provider = FastFlightsProvider()
    melhor = type("F", (), {
        "price": 2980,
        "airlines": [type("A", (), {"name": "LATAM", "code": "LA"})()],
        "flights": [PernaFalsa("GRU", "CDG"), PernaFalsa("CDG", "LIS"), PernaFalsa("LIS", "GRU")],
    })()

    resultado = provider._melhor_oferta([melhor], pedido(max_stops=0))

    assert resultado is None


def test_escolhe_a_oferta_mais_barata():
    provider = FastFlightsProvider()

    def voo(preco):
        return type("F", (), {
            "price": preco,
            "airlines": [type("A", (), {"name": "TAP", "code": "TP"})()],
            "flights": [PernaFalsa("GRU", "LIS"), PernaFalsa("LIS", "GRU")],
        })()

    resultado = provider._melhor_oferta([voo(4200), voo(2980), voo(3500)], pedido())

    assert resultado.price == Decimal("2980")
    assert resultado.stops == 0
    assert resultado.airline == "TAP"
    assert resultado.departure_airport == "GRU"
    assert resultado.source == "FAST_FLIGHTS"


def test_resultado_sem_preco_valido_nao_confirma():
    provider = FastFlightsProvider()
    lixo = type("F", (), {"price": None, "airlines": [], "flights": []})()

    assert provider._melhor_oferta([lixo], pedido()) is None


# ------------------------------- degradacao parcial da biblioteca (observada)

def test_companhia_como_string_e_extraida():
    """A anotacao promete list[Airline], mas em execucao real vem list[str]."""
    from app.boundary.gateway.fastflights import _nome_da_cia

    assert _nome_da_cia("Tap Air Portugal") == "Tap Air Portugal"
    assert _nome_da_cia(type("A", (), {"name": "LATAM"})()) == "LATAM"
    assert _nome_da_cia(None) is None
    assert _nome_da_cia("   ") is None


def test_codigo_da_cia_so_aceita_o_que_parece_codigo():
    """`type` guarda o codigo IATA hoje; se passar a guardar outra coisa, ignoramos."""
    from app.boundary.gateway.fastflights import _codigo_da_cia

    assert _codigo_da_cia(type("F", (), {"type": "TP"})()) == "TP"
    assert _codigo_da_cia(type("F", (), {"type": "round-trip"})()) is None
    assert _codigo_da_cia(type("F", (), {"type": None})()) is None


def test_horario_com_hora_ausente_nao_estoura():
    """Formato real observado: time=[None, 45]. Minuto presente, hora ausente."""
    from app.boundary.gateway.fastflights import _instante

    quebrado = type("D", (), {"date": [2026, 9, 23], "time": [None, 45]})()
    assert _instante(quebrado) is None

    inteiro = type("D", (), {"date": [2026, 9, 23], "time": [14, 35]})()
    assert _instante(inteiro).hour == 14


@pytest.mark.anyio
async def test_confirmacao_incompleta_gera_aviso():
    """Perder campo em silencio esconderia a degradacao gradual da camada 2."""
    incompleta = oferta(airline=None, departure_at=None, stops=0)
    r = await ConfirmationChain([ProviderFalso("p", resultado=incompleta)]).confirm(pedido())

    assert r.confirmed is True
    assert any("nao trouxe" in a and "companhia" in a for a in r.warnings)
