"""Contratos das fontes de preco.

# Por que DUAS interfaces e nao uma

A tentacao e criar um unico `PriceProvider` e fazer todas as fontes o
implementarem. Seria errado: as duas camadas nao fazem a mesma coisa.

    Camada 1 (varredura)      Camada 2 (confirmacao)
    --------------------      ----------------------
    30 datas por chamada      1 data por chamada
    barata, pode rodar        cara, so roda quando ha
    a cada 6 horas            candidato abaixo do teto
    preco de cidade           aeroporto, cia, horario
    cacheada                  ao vivo
    muitas ofertas            uma oferta, ou nenhuma

Forcar as duas numa interface so criaria uma simetria falsa: metade dos
metodos nao faria sentido para metade das implementacoes.

# Onde o Strategy realmente se aplica

Dentro de cada camada. Para confirmar um candidato, `fast-flights`, SerpApi ou
Playwright sao genuinamente intercambiaveis — mesma entrada, mesma saida, custo
e confiabilidade diferentes. E exatamente o caso de uso do padrao.

Usamos `Protocol` em vez de classe base abstrata: a conformidade e estrutural,
entao um provider novo nao precisa herdar de nada nem conhecer este modulo.
"""

from typing import Protocol, runtime_checkable

from app.schemas import (
    CalendarSearchRequest,
    CalendarSearchResponse,
    ConfirmedOffer,
    ConfirmRequest,
)


class ProviderError(RuntimeError):
    """Falha de uma fonte. Quem chama decide se degrada ou aborta."""

    def __init__(self, provider: str, mensagem: str) -> None:
        super().__init__(f"[{provider}] {mensagem}")
        self.provider = provider
        self.mensagem = mensagem


@runtime_checkable
class CalendarProvider(Protocol):
    """Camada 1: varre uma janela de datas e devolve candidatos.

    Implementacoes levantam `ProviderError` quando a fonte falha. O roteador
    traduz isso em 502, e o core-java marca a busca como FAILED — sem preco nao
    ha varredura, entao aqui degradar nao e opcao.
    """

    name: str

    async def buscar(self, req: CalendarSearchRequest) -> CalendarSearchResponse: ...


@runtime_checkable
class ConfirmationProvider(Protocol):
    """Camada 2: confirma um candidato com dados reais de voo.

    Implementacoes devem levantar `ProviderError` em qualquer falha — de rede,
    de formato ou de mudanca na fonte. Devolver `None` significa "consultei e
    nao existe voo assim", que e diferente de "nao consegui consultar".
    """

    name: str

    async def confirm(self, req: ConfirmRequest) -> ConfirmedOffer | None: ...
