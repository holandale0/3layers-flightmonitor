"""Interpretacao de linguagem natural — etapa E3.1.

Todos os testes fixam `hoje`. "Em marco" significa coisas diferentes em janeiro
e em abril, e um teste dependente do relogio da maquina passaria a falhar
sozinho na virada do ano — do tipo que se descobre num sabado.
"""

from datetime import date
from decimal import Decimal

import pytest

from app.composicao.nlp import build_intent_chain
from app.config import Settings
from app.control.nlp.aeroportos import procurar_no_texto, resolver
from app.control.nlp.cadeia import IntentChain
from app.control.nlp.portas import IntentError
from app.control.nlp.regras import RegrasIntentProvider
from app.schemas import IntentRequest, MonitorIntent

HOJE = date(2026, 8, 12)


async def interpretar(texto: str, origem_padrao: str | None = "GRU") -> MonitorIntent:
    return await RegrasIntentProvider().interpretar(
        IntentRequest(texto=texto, hoje=HOJE, origem_padrao=origem_padrao)
    )


# --------------------------------------------------------------- aeroportos


def test_resolve_nome_codigo_e_desconhecido():
    assert resolver("Lisboa") == "LIS"
    assert resolver("  são paulo ") == "GRU"
    # Codigo IATA passa direto: quem ja sabe o codigo nao deve ser atrapalhado.
    assert resolver("MAD") == "MAD"
    # Desconhecido devolve None, e o chamador transforma em aviso. Chutar
    # aeroporto faria o monitor vigiar a rota errada por meses, em silencio.
    assert resolver("Xanadu") is None
    assert resolver(None) is None


def test_nome_mais_longo_vence_o_mais_curto():
    achados = procurar_no_texto("de Porto Alegre para Lisboa")

    # "Porto" sozinho e OPO, em outro continente. Ler "Porto Alegre" como
    # "Porto" mandaria o monitor para o lugar errado.
    assert [c for c, _, _ in achados] == ["POA", "LIS"]


def test_posicao_permite_distinguir_origem_de_destino():
    achados = procurar_no_texto("do Rio para Lisboa")

    assert [c for c, _, _ in achados] == ["GIG", "LIS"]
    assert achados[0][2] < achados[1][2]


# ------------------------------------------------------------------ rota


@pytest.mark.anyio
async def test_pedido_completo():
    r = await interpretar(
        "Quero ir de São Paulo para Lisboa em março por até 4 mil, voo direto, uma semana"
    )

    assert (r.origin, r.destination) == ("GRU", "LIS")
    assert (r.departure_from, r.departure_to) == (date(2027, 3, 1), date(2027, 3, 31))
    assert r.max_price == Decimal("4000")
    assert r.prefere_voo_direto is True
    assert (r.min_stay_days, r.max_stay_days) == (7, 9)
    assert r.confianca == 1.0
    assert r.avisos == []


@pytest.mark.anyio
async def test_uma_cidade_so_e_o_destino():
    r = await interpretar("quero ir pra Belém em dezembro, até R$ 1.500")

    # "Quero ir pra X" quase nunca informa a origem: ela vem do padrao.
    assert (r.origin, r.destination) == ("GRU", "BEL")


@pytest.mark.anyio
async def test_sem_origem_padrao_avisa_em_vez_de_chutar():
    r = await interpretar("quero ir pra Belém em dezembro por 1500 reais", origem_padrao=None)

    assert r.origin is None
    assert r.destination == "BEL"
    assert any("origem" in a for a in r.avisos)
    assert r.completo() is False


@pytest.mark.anyio
async def test_marca_decide_quem_e_origem_mesmo_fora_de_ordem():
    r = await interpretar("passagem para Lisboa saindo de Recife em maio por 3 mil")

    # A ordem no texto poria Lisboa como origem. A marca "saindo de" corrige.
    assert (r.origin, r.destination) == ("REC", "LIS")


# ---------------------------------------------------------------- datas


@pytest.mark.anyio
async def test_mes_ja_passado_vai_para_o_ano_seguinte():
    # Estamos em agosto de 2026; "março" so pode ser 2027.
    r = await interpretar("Lisboa em março por 4 mil")

    assert r.departure_from == date(2027, 3, 1)


@pytest.mark.anyio
async def test_mes_ainda_por_vir_fica_no_ano_corrente():
    r = await interpretar("Lisboa em dezembro por 4 mil")

    assert r.departure_from == date(2026, 12, 1)


@pytest.mark.anyio
async def test_ano_explicito_vence_a_regra():
    r = await interpretar("Lisboa em março de 2029 por 4 mil")

    assert r.departure_from == date(2029, 3, 1)


@pytest.mark.anyio
async def test_intervalo_de_dias_no_mes():
    r = await interpretar("Buenos Aires entre 10 e 20 de julho por 2500 reais")

    assert (r.departure_from, r.departure_to) == (date(2027, 7, 10), date(2027, 7, 20))


@pytest.mark.anyio
async def test_fevereiro_de_ano_bissexto_termina_no_dia_certo():
    r = await interpretar("Lisboa em fevereiro de 2028 por 4 mil")

    # 2028 e bissexto. Fixar 28 no codigo daria uma janela um dia menor.
    assert r.departure_to == date(2028, 2, 29)


# ---------------------------------------------------------------- preco


@pytest.mark.anyio
@pytest.mark.parametrize(
    ("trecho", "esperado"),
    [
        ("até 4 mil", "4000"),
        ("por 2500 reais", "2500.00"),
        ("até R$ 1.500", "1500.00"),
        ("no máximo 12000", "12000.00"),
        ("R$ 890,50", "890.50"),
    ],
)
async def test_formatos_de_preco(trecho, esperado):
    r = await interpretar(f"Lisboa em março {trecho}")

    assert r.max_price == Decimal(esperado)


@pytest.mark.anyio
async def test_preco_nao_e_cortado_no_meio():
    """O defeito que o teste pegou: 2500 virava 250.

    Um monitor de R$ 250 para Lisboa nunca alertaria, e nao haveria como o
    usuario descobrir por que — a interface mostraria o numero errado como se
    fosse o que ele pediu.
    """
    r = await interpretar("Lisboa em março por 2500 reais")

    assert r.max_price == Decimal("2500.00")


# ----------------------------------------------------- escalas e detalhes


@pytest.mark.anyio
async def test_voo_direto_e_preferencia_e_nao_limite():
    r = await interpretar("Lisboa em março por 4 mil, voo direto")

    assert r.prefere_voo_direto is True
    # `max_stops` fica nulo: a pessoa disse que prefere, nao que exclui. Fixar
    # zero aqui descartaria uma oferta com escala muito mais barata.
    assert r.max_stops is None


@pytest.mark.anyio
async def test_somente_direto_vira_limite():
    r = await interpretar("Lisboa em março por 4 mil, somente voo direto")

    assert r.max_stops == 0
    assert r.prefere_voo_direto is True


@pytest.mark.anyio
async def test_limite_de_escalas():
    r = await interpretar("Lisboa em março por 4 mil, no máximo 1 escala")

    assert r.max_stops == 1


@pytest.mark.anyio
async def test_passageiros():
    assert (await interpretar("Lisboa em março por 4 mil para 3 pessoas")).passengers == 3
    assert (await interpretar("Lisboa em março por 4 mil, somos um casal")).passengers == 2


# ----------------------------------------------- o que NAO se sabe dizer


@pytest.mark.anyio
async def test_pedido_vago_nao_inventa_nada():
    r = await interpretar("quero viajar barato")

    assert r.destination is None
    assert r.departure_from is None
    assert r.max_price is None
    assert r.completo() is False
    # A confianca cai, e os avisos dizem exatamente o que faltou.
    assert r.confianca < 0.5
    assert set(r.faltando()) == {"destino", "periodo da viagem", "preco maximo"}


@pytest.mark.anyio
async def test_cidade_desconhecida_nao_vira_chute():
    r = await interpretar("quero ir pra Xanadu em março por 4 mil")

    assert r.destination is None
    assert any("destino" in a for a in r.avisos)


@pytest.mark.anyio
async def test_confianca_mede_o_que_existe_e_nao_o_que_esta_certo():
    completo = await interpretar("de Recife para Lisboa em março por 4 mil")
    parcial = await interpretar("Lisboa em março")

    assert completo.confianca == 1.0
    # Origem (do padrao), destino e as duas datas: quatro de cinco. So o preco
    # falta — e e justamente ele que impede o monitor de existir.
    assert parcial.confianca == 0.8
    assert parcial.completo() is False


# ---------------------------------------------------------------- cadeia


class ProviderQueFalha:
    name = "falso-quebrado"

    async def interpretar(self, req):
        raise IntentError(self.name, "modelo fora do ar")


class ProviderQueExplode:
    name = "falso-explosivo"

    async def interpretar(self, req):
        raise ValueError("erro que ninguem previu")


@pytest.mark.anyio
async def test_cadeia_cai_para_as_regras_quando_o_modelo_falha():
    cadeia = IntentChain([ProviderQueFalha(), RegrasIntentProvider()])

    r = await cadeia.interpretar(
        IntentRequest(texto="Lisboa em março por 4 mil", hoje=HOJE, origem_padrao="GRU")
    )

    assert r.destination == "LIS"
    assert r.provider == "regras"
    # O usuario merece saber que veio do plano B: a interpretacao por regras
    # entende menos, e isso explica um resultado mais pobre.
    assert any("opcao preferida falhou" in a for a in r.avisos)


@pytest.mark.anyio
async def test_provider_mal_comportado_nao_derruba_a_cadeia():
    cadeia = IntentChain([ProviderQueExplode(), RegrasIntentProvider()])

    r = await cadeia.interpretar(IntentRequest(texto="Lisboa em março por 4 mil", hoje=HOJE))

    assert r.destination == "LIS"


def test_fabrica_sempre_termina_nas_regras():
    sem_chave = Settings(travelpayouts_token="x")
    com_chave = Settings(travelpayouts_token="x", anthropic_api_key="sk-teste")

    # Sem chave: so regras.
    assert build_intent_chain(sem_chave).provider_names == ["regras"]
    # Com chave: modelo primeiro, regras por ultimo. Inverter faria a chamada
    # paga acontecer so quando ela nao fosse necessaria.
    assert build_intent_chain(com_chave).provider_names == ["claude", "regras"]


def test_regras_nunca_saem_da_cadeia():
    """E o que garante que a cadeia nunca fique vazia.

    Uma chave revogada vira uma resposta mais pobre, e nao um erro 500.
    """
    for settings in (Settings(travelpayouts_token="x"),
                     Settings(travelpayouts_token="x", anthropic_api_key="sk-teste")):
        assert build_intent_chain(settings).provider_names[-1] == "regras"
