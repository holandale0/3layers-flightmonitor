"""Contrato entre o core-java e o worker.

O worker e um especialista burro: recebe rota e janela de datas, devolve ofertas.
Nao conhece monitores, nao decide se um preco e bom. Ver docs/PLANO-DE-ACAO.md
secao 3.
"""

from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel, Field, field_validator

IATA = Field(min_length=3, max_length=3, pattern=r"^[A-Z]{3}$")


class CalendarSearchRequest(BaseModel):
    """Varredura ampla de uma janela de datas (camada 1)."""

    origin: str = IATA
    destination: str = IATA
    departure_from: date
    departure_to: date
    return_from: date | None = None
    return_to: date | None = None
    currency: str = Field(default="BRL", pattern=r"^[A-Z]{3}$")
    max_stops: int | None = Field(default=None, ge=0)

    @field_validator("origin", "destination", "currency", mode="before")
    @classmethod
    def maiusculas(cls, v: str) -> str:
        return v.strip().upper() if isinstance(v, str) else v

    def validar_janela(self) -> None:
        if self.departure_to < self.departure_from:
            raise ValueError("departure_to nao pode ser anterior a departure_from")
        if (self.return_from is None) != (self.return_to is None):
            raise ValueError("a janela de volta precisa de inicio e fim, ou nenhum dos dois")
        if self.return_to is not None and self.return_to < self.return_from:
            raise ValueError("return_to nao pode ser anterior a return_from")


class FlightOffer(BaseModel):
    """Uma oferta de preco para um par de datas."""

    departure_date: date
    return_date: date | None = None
    price: Decimal
    currency: str
    airline: str | None = None
    flight_number: str | None = None
    stops: int | None = None
    # Duracao total da viagem, em minutos. Nem toda fonte informa: o calendario
    # de ida e volta nao traz, o endpoint de so ida traz. Nulo quando a fonte
    # nao diz — e nao zero, que significaria voo instantaneo.
    duration_minutes: int | None = None
    # Horario local do aeroporto, sem fuso — coerente com o schema do banco.
    departure_at: datetime | None = None
    arrival_at: datetime | None = None
    # Ate quando o preco cacheado vale. Insumo contra falso-positivo (RISCO-003).
    expires_at: datetime | None = None
    source: str


class ConfirmRequest(BaseModel):
    """Confirmacao de um candidato especifico (camada 2)."""

    origin: str = IATA
    destination: str = IATA
    departure_date: date
    return_date: date | None = None
    currency: str = Field(default="BRL", pattern=r"^[A-Z]{3}$")
    max_stops: int | None = Field(default=None, ge=0)
    passengers: int = Field(default=1, ge=1, le=9)
    # Preco visto pela camada 1. Serve para medir a divergencia entre o preco
    # cacheado e o preco real — a metrica de falso-positivo do RISCO-003.
    candidate_price: Decimal | None = None

    @field_validator("origin", "destination", "currency", mode="before")
    @classmethod
    def maiusculas(cls, v: str) -> str:
        return v.strip().upper() if isinstance(v, str) else v


class ConfirmedOffer(BaseModel):
    """Oferta confirmada, com os dados de voo que a camada 1 nao tem."""

    departure_date: date
    return_date: date | None = None
    price: Decimal
    currency: str
    airline: str | None = None
    airline_code: str | None = None
    stops: int | None = None
    duration_minutes: int | None = None
    departure_at: datetime | None = None
    arrival_at: datetime | None = None
    # Aeroportos reais, ja que a camada 1 so devolve codigo de cidade (RISCO-006).
    departure_airport: str | None = None
    arrival_airport: str | None = None
    source: str


class ProviderAttempt(BaseModel):
    """Rastro de uma tentativa da cadeia de confirmacao.

    Existe para que a queda de um provider seja visivel como dado, e nao
    apenas como ausencia de alertas.
    """

    provider: str
    ok: bool
    found: bool = False
    error: str | None = None
    duration_ms: int = 0


class ConfirmResponse(BaseModel):
    """Resultado da confirmacao.

    Tres desfechos distintos, que o core-java precisa saber diferenciar:

      confirmed=True                 -> ha voo real, use estes dados
      confirmed=False, degraded=False -> consultamos e NAO existe voo assim;
                                         o candidato da camada 1 era ilusorio
      confirmed=False, degraded=True  -> nenhuma fonte respondeu; nao sabemos.
                                         O sistema segue vivo, so cego nesta camada
    """

    confirmed: bool = False
    degraded: bool = False
    offer: ConfirmedOffer | None = None
    provider: str | None = None
    attempts: list[ProviderAttempt] = []
    warnings: list[str] = []


class CalendarSearchResponse(BaseModel):
    """Resultado da varredura, com rastro do que foi descartado e por que.

    Os contadores nao sao decoracao: sao o que permite diagnosticar um provider
    degradado sem ler log. Se `returned` for alto e `kept` for zero, o problema
    esta no filtro; se `returned` for zero, o problema esta na fonte.
    """

    origin: str
    destination: str
    offers: list[FlightOffer] = []
    returned: int = 0
    kept: int = 0
    provider_origin: str | None = None
    provider_destination: str | None = None
    warnings: list[str] = []


# ---------------------------------------------------------------------------
# Linguagem natural — etapa E3.1
# ---------------------------------------------------------------------------


class IntentRequest(BaseModel):
    """Um pedido em texto livre.

    `hoje` existe para o teste poder fixar a data. "Em marco" significa coisas
    diferentes em janeiro e em abril, e um teste que dependesse do relogio da
    maquina passaria a falhar sozinho na virada do ano.
    """

    texto: str = Field(min_length=3, max_length=1000)
    hoje: date | None = None
    origem_padrao: str | None = Field(default=None, pattern=r"^[A-Za-z]{3}$")


class MonitorIntent(BaseModel):
    """O que o texto pediu, em campos.

    **Nada aqui e inventado.** Campo que nao deu para extrair volta nulo, e o
    motivo entra em `avisos`. Um monitor montado a partir de chute vigiaria a
    rota errada por meses em silencio — parece que esta funcionando, e nao esta.

    O consumidor (o core-java, na E3.2) decide o que fazer com o que faltou:
    perguntar, usar padrao, ou recusar.
    """

    origin: str | None = None
    destination: str | None = None
    departure_from: date | None = None
    departure_to: date | None = None
    min_stay_days: int | None = None
    max_stay_days: int | None = None
    max_price: Decimal | None = None
    currency: str = "BRL"
    max_stops: int | None = None
    passengers: int | None = None
    prefere_voo_direto: bool = False
    avoided_airlines: list[str] = []
    label: str | None = None

    #: Quem interpretou: "regras" ou "claude". Vai na resposta porque muda o
    #: quanto se pode confiar no resultado.
    provider: str = "regras"
    #: 0 a 1. Quantos dos campos essenciais foram realmente encontrados.
    confianca: float = 0.0
    avisos: list[str] = []

    def completo(self) -> bool:
        """Tem o minimo para virar monitor: rota, janela e teto de preco."""
        return all([
            self.origin,
            self.destination,
            self.departure_from,
            self.departure_to,
            self.max_price,
        ])

    def faltando(self) -> list[str]:
        ausentes = []
        if not self.origin:
            ausentes.append("origem")
        if not self.destination:
            ausentes.append("destino")
        if not self.departure_from or not self.departure_to:
            ausentes.append("periodo da viagem")
        if not self.max_price:
            ausentes.append("preco maximo")
        return ausentes
