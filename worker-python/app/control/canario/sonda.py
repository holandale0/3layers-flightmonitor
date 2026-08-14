"""O canario das fontes externas — etapa E4.5.

# O que ele verifica, e o que ele NAO verifica

Ele **nao** valida logica de negocio, e nao pergunta se o preco esta bom. Ele
pergunta uma coisa so: *as fontes ainda respondem no formato que o codigo
espera?*

E a unica defesa contra o RISCO-002 — descobrir que o Google mudou o formato
**antes** de voce deixar de receber alertas, e nao seis semanas depois.

# Por que formato, e nao disponibilidade

"A fonte respondeu 200" nao protege de nada. As tres quebras que este projeto ja
viu na camada 2 passariam por qualquer teste de disponibilidade:

| O que aconteceu | O que um ping teria dito |
|---|---|
| a API da biblioteca mudou inteira entre 2.x e 3.x | nada — nem chegou a rodar |
| `list[Airline]` anotado, `list[str]` em execucao | 200 OK |
| o parser devolveu `time=[None, 45]`, sem a hora | 200 OK |

Por isso a sonda olha **campo por campo**: presenca, tipo e plausibilidade. Um
preco que vem como texto, uma data nula, uma lista vazia onde deveria haver
conteudo — cada um e um sinal, e nenhum aparece no codigo HTTP.

# Fora do CI, de proposito

Depende de rede, consome cota e e intrinsecamente instavel. Canario vermelho e
informacao; canario no CI seria ruido que treina todo mundo a ignorar falha.
Ver secao 9 do PLANO-DE-ACAO.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import Decimal

from app.control.busca.cadeia import ConfirmationChain
from app.control.busca.portas import CalendarProvider
from app.schemas import CalendarSearchRequest, ConfirmRequest

# Rota de sonda: par movimentado, com voo todo dia do ano. Rota rara devolveria
# vazio por falta de voo, e "vazio" seria confundido com "a fonte quebrou".
ORIGEM_SONDA = "GRU"
DESTINO_SONDA = "GIG"

# Trinta dias a frente: perto o bastante para existir preco, longe o bastante
# para nao cair na janela de ultima hora, onde a oferta some.
DIAS_A_FRENTE = 30

# Quantas ofertas conferir campo a campo. Conferir todas so repetiria o mesmo
# diagnostico dezenas de vezes; o que interessa e se a FORMA mudou.
AMOSTRA = 5


@dataclass
class Achado:
    """Um problema de formato encontrado numa resposta real."""

    campo: str
    esperado: str
    recebido: str

    def __str__(self) -> str:
        return f"{self.campo}: esperava {self.esperado}, veio {self.recebido}"


@dataclass
class ResultadoDaSonda:
    camada: str
    provider: str
    respondeu: bool = False
    ofertas: int = 0
    duracao_ms: int = 0
    erro: str | None = None
    achados: list[Achado] = field(default_factory=list)

    @property
    def formato_ok(self) -> bool:
        """Respondeu E no formato esperado.

        As duas coisas juntas: fonte que responde no formato errado e tao inutil
        quanto fonte fora do ar — e mais perigosa, porque parece funcionar.
        """
        return self.respondeu and not self.achados

    def para_dict(self) -> dict:
        return {
            "camada": self.camada,
            "provider": self.provider,
            "respondeu": self.respondeu,
            "formato_ok": self.formato_ok,
            "ofertas": self.ofertas,
            "duracao_ms": self.duracao_ms,
            "erro": self.erro,
            "achados": [str(a) for a in self.achados],
        }


def _conferir(oferta, onde: str) -> list[Achado]:
    """Campo por campo, so no que o codigo realmente depende.

    `flight_number` ausente, por exemplo, nao entra: o sistema vive sem ele, e
    transformar isso em alarme ensina a ignorar o alarme.
    """
    achados: list[Achado] = []

    preco = getattr(oferta, "price", None)
    if not isinstance(preco, Decimal):
        achados.append(Achado(f"{onde}.price", "Decimal", type(preco).__name__))
    elif preco <= 0:
        achados.append(Achado(f"{onde}.price", "maior que zero", str(preco)))

    partida = getattr(oferta, "departure_date", None)
    if not isinstance(partida, date):
        achados.append(Achado(f"{onde}.departure_date", "date", type(partida).__name__))

    moeda = getattr(oferta, "currency", None)
    if not isinstance(moeda, str) or len(moeda) != 3:
        achados.append(Achado(f"{onde}.currency", "codigo ISO de 3 letras", repr(moeda)))

    # `airline` anotado como str ja voltou como outra coisa — foi uma das tres
    # quebras reais. O tipo importa mais que o valor.
    cia = getattr(oferta, "airline", None)
    if cia is not None and not isinstance(cia, str):
        achados.append(Achado(f"{onde}.airline", "str ou None", type(cia).__name__))

    escalas = getattr(oferta, "stops", None)
    if escalas is not None and not isinstance(escalas, int):
        achados.append(Achado(f"{onde}.stops", "int ou None", type(escalas).__name__))

    return achados


async def sondar_camada1(provider: CalendarProvider) -> ResultadoDaSonda:
    """A varredura ampla, contra a API real."""
    resultado = ResultadoDaSonda(camada="1", provider=provider.name)
    partida = date.today() + timedelta(days=DIAS_A_FRENTE)
    inicio = time.monotonic()

    try:
        resposta = await provider.buscar(CalendarSearchRequest(
            origin=ORIGEM_SONDA,
            destination=DESTINO_SONDA,
            departure_from=partida,
            departure_to=partida + timedelta(days=7),
            currency="BRL",
        ))
        resultado.respondeu = True
        resultado.ofertas = len(resposta.offers)

        if not resposta.offers:
            # Numa rota como GRU-GIG, sete dias sem nenhum preco nao e ausencia
            # de promocao: e sinal de que a resposta mudou de forma.
            resultado.achados.append(
                Achado("offers", "ao menos uma na rota de sonda", "nenhuma"))

        for i, oferta in enumerate(resposta.offers[:AMOSTRA]):
            resultado.achados.extend(_conferir(oferta, f"offers[{i}]"))

    except Exception as e:  # noqa: BLE001 — o canario reporta, nunca propaga
        resultado.erro = f"{type(e).__name__}: {e}"

    resultado.duracao_ms = int((time.monotonic() - inicio) * 1000)
    return resultado


async def sondar_camada2(cadeia: ConfirmationChain) -> ResultadoDaSonda:
    """A confirmacao pontual — a camada fragil, e a razao desta etapa existir.

    Recebe a CADEIA, e nao um provider: e assim que a camada 2 e usada de
    verdade, e sondar de outro jeito testaria um caminho que ninguem percorre.
    """
    resultado = ResultadoDaSonda(camada="2", provider=", ".join(cadeia.provider_names) or "(nenhum)")
    partida = date.today() + timedelta(days=DIAS_A_FRENTE)
    inicio = time.monotonic()

    try:
        resposta = await cadeia.confirm(ConfirmRequest(
            origin=ORIGEM_SONDA,
            destination=DESTINO_SONDA,
            departure_date=partida,
            currency="BRL",
        ))
        resultado.respondeu = not resposta.degraded
        resultado.ofertas = 1 if resposta.confirmed else 0

        if resposta.degraded:
            # `degraded` e "nenhuma fonte respondeu" — exatamente o estado que
            # o BUG-014 produziu por seis semanas.
            resultado.erro = "; ".join(resposta.warnings) or "cadeia degradada"
        elif not resposta.confirmed:
            # Consultou e disse que nao existe voo assim. Numa rota GRU-GIG a
            # 30 dias, isso nao e uma resposta plausivel — e sinal de formato.
            resultado.achados.append(
                Achado("confirmed", "um voo na rota de sonda", "False"))
        elif resposta.offer is not None:
            resultado.achados.extend(_conferir(resposta.offer, "offer"))

    except Exception as e:  # noqa: BLE001
        resultado.erro = f"{type(e).__name__}: {e}"

    resultado.duracao_ms = int((time.monotonic() - inicio) * 1000)
    return resultado
