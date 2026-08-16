"""Monitor de somente ida recebe preco de somente ida — o BUG-016.

# O que aconteceu

Um monitor GRU -> BEL configurado como **somente ida** gravava observacoes com
data de volta, e o preco gravado era o do par ida+volta. Ele era entao comparado
com um teto que a pessoa escolheu pensando em so ida.

# Por que passou despercebido

O filtro era **assimetrico**:

    if req.return_from is not None:      # tem janela de volta -> confere
        ...
    # sem janela de volta -> NAO CONFERE NADA

"Somente ida" era expresso como *ausencia* de janela de volta, e o codigo lia
essa ausencia como "tanto faz" em vez de "nao pode ter volta".

# O dano real

Nao era so dado feio na tela. Testado contra a API real em 16/08/2026, para
GRU -> BEL em dezembro:

| Consulta | Preco |
|---|---|
| ida e volta (o que era gravado) | R$ 1.674 |
| **so ida** (o que devia ser gravado) | **R$ 1.004** |

Um monitor de so ida com teto de R$ 1.100 nunca teria alertado, porque comparava
R$ 1.674 com o teto. O sistema estava **cego para oportunidades de so ida** — e
a estatistica da Fase 2 (mediana, anomalia, tendencia) foi calculada em cima do
preco do produto errado.
"""

from datetime import date

import pytest

from app.boundary.gateway.travelpayouts import TravelpayoutsProvider
from app.schemas import CalendarSearchRequest


def pedido_so_ida(**extra) -> CalendarSearchRequest:
    base = {
        "origin": "GRU",
        "destination": "BEL",
        "departure_from": date(2026, 12, 1),
        "departure_to": date(2026, 12, 31),
    }
    base.update(extra)
    return CalendarSearchRequest(**base)


def entrada(depart="2026-12-19", volta="", preco=1004, escalas=0, duracao=425, gate="Kiwi.com"):
    """Uma entrada do `v2/prices/latest`, no formato real da API."""
    return {
        "origin": "SAO",
        "destination": "BEL",
        "depart_date": depart,
        "return_date": volta,
        "value": preco,
        "number_of_changes": escalas,
        "duration": duracao,
        "gate": gate,
        "trip_class": 0,
    }


class ClienteFalso:
    """Devolve uma resposta fixa e guarda o que foi pedido."""

    def __init__(self, dados):
        self._dados = dados
        self.url = None
        self.params = None

    async def get(self, url, params=None):
        self.url = url
        self.params = params
        return self

    def raise_for_status(self):
        return None

    def json(self):
        return {"success": True, "data": self._dados}

    async def aclose(self):
        return None


@pytest.mark.anyio
async def test_pedido_de_so_ida_usa_o_endpoint_que_respeita_one_way():
    """A correcao de fundo: perguntar a coisa certa.

    Foi testado contra a API real que o `v1/prices/calendar` IGNORA `one_way` —
    com `true`, com `1` e sem o parametro, ele sempre devolve `return_at`.
    Filtrar depois deixaria o monitor de so ida sem nenhum preco.
    """
    cliente = ClienteFalso([entrada()])
    provider = TravelpayoutsProvider(token="t")

    await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert "v2/prices/latest" in cliente.url
    assert cliente.params["one_way"] == "true"


@pytest.mark.anyio
async def test_oferta_de_so_ida_nao_tem_data_de_volta():
    # O sintoma que o usuario viu na tela: coluna "Volta" preenchida num
    # monitor de somente ida.
    cliente = ClienteFalso([entrada()])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert r.kept == 1
    assert r.offers[0].return_date is None
    assert r.offers[0].price == 1004


@pytest.mark.anyio
async def test_oferta_com_volta_e_descartada_num_pedido_de_so_ida():
    """Cinto e suspensorio.

    O endpoint promete respeitar `one_way`, e o BUG-016 nasceu justamente de
    confiar numa promessa dessas sem conferir.
    """
    cliente = ClienteFalso([entrada(volta="2026-12-26"), entrada(depart="2026-12-20")])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert r.kept == 1
    assert r.offers[0].departure_date == date(2026, 12, 20)
    assert any("somente ida" in a for a in r.warnings)


@pytest.mark.anyio
async def test_pedido_com_volta_continua_no_calendario():
    # A correcao nao pode quebrar quem quer ida e volta: sao dois caminhos, e
    # cada um responde uma pergunta diferente.
    cliente = ClienteFalso({})
    provider = TravelpayoutsProvider(token="t")

    await provider.buscar(
        pedido_so_ida(return_from=date(2026, 12, 20), return_to=date(2026, 12, 30)),
        cliente=cliente,
    )

    assert "v1/prices/calendar" in cliente.url
    assert "one_way" not in (cliente.params or {})


@pytest.mark.anyio
async def test_data_fora_da_janela_e_descartada():
    # RISCO-007: nunca confiar que o provider respeitou o periodo pedido.
    cliente = ClienteFalso([entrada(depart="2027-05-10")])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert r.kept == 0
    assert any("fora dos criterios" in a for a in r.warnings)


@pytest.mark.anyio
async def test_filtra_por_escalas_tambem_no_caminho_de_so_ida():
    cliente = ClienteFalso([entrada(escalas=2), entrada(depart="2026-12-20", escalas=0)])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(max_stops=0), cliente=cliente)

    assert r.kept == 1
    assert r.offers[0].stops == 0


@pytest.mark.anyio
async def test_sem_horario_de_chegada_o_campo_fica_nulo():
    """O segundo defeito achado no mesmo lugar.

    O calendario nao devolve horario de chegada, e o codigo punha `return_at`
    em `arrival_at` — que e a partida da VOLTA. Gravava horario errado no
    historico; nulo diz a verdade.
    """
    cliente = ClienteFalso([entrada()])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert r.offers[0].arrival_at is None


# ------------------------------------------------------ duracao e companhia


@pytest.mark.anyio
async def test_duracao_do_voo_vem_da_fonte():
    # O endpoint de so ida informa `duration` em minutos. E a unica das duas
    # fontes da camada 1 que informa.
    cliente = ClienteFalso([entrada(duracao=990)])
    provider = TravelpayoutsProvider(token="t")

    r = await provider.buscar(pedido_so_ida(), cliente=cliente)

    assert r.offers[0].duration_minutes == 990


@pytest.mark.anyio
async def test_duracao_invalida_vira_nulo():
    """Zero nao e "voo instantaneo", e dado quebrado.

    Mostrar "0h00" na tela seria pior que mostrar travessao: um diz uma
    inverdade, o outro admite que nao se sabe.
    """
    for ruim in (0, -30, None, "muito tempo"):
        cliente = ClienteFalso([entrada(duracao=ruim)])
        r = await TravelpayoutsProvider(token="t").buscar(pedido_so_ida(), cliente=cliente)
        assert r.offers[0].duration_minutes is None, ruim


@pytest.mark.anyio
async def test_agencia_vai_para_campo_proprio_e_nao_para_companhia():
    """`gate` e quem VENDE, e nao quem opera o voo.

    As duas coisas ficam separadas por um motivo que vai alem da tela: o core
    compara `airline` com a lista de companhias que o monitor evita. Agencia
    naquele campo faria "evitar GOL" comparar GOL com Kiwi.com — e a
    preferencia pararia de funcionar em silencio.
    """
    cliente = ClienteFalso([entrada(gate="Mytrip.com")])

    r = await TravelpayoutsProvider(token="t").buscar(pedido_so_ida(), cliente=cliente)

    assert r.offers[0].airline is None
    assert r.offers[0].agency == "Mytrip.com"


@pytest.mark.anyio
async def test_a_referencia_para_comprar_nunca_se_perde():
    """O motivo de a agencia ser guardada, e nao descartada.

    Sem nenhum dos dois campos, a tabela mostraria travessao — e quem visse a
    oferta nao teria onde ir compra-la. Guardar em campo proprio mantem a
    informacao util sem misturar significados.
    """
    cliente = ClienteFalso([entrada(gate="Kiwi.com")])

    r = await TravelpayoutsProvider(token="t").buscar(pedido_so_ida(), cliente=cliente)
    oferta = r.offers[0]

    assert (oferta.airline or oferta.agency) is not None


@pytest.mark.anyio
async def test_gate_vazio_nao_vira_agencia_em_branco():
    cliente = ClienteFalso([entrada(gate="")])

    r = await TravelpayoutsProvider(token="t").buscar(pedido_so_ida(), cliente=cliente)

    assert r.offers[0].agency is None
