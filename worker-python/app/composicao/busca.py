"""Montagem das cadeias de providers.

Ponto unico de registro: adicionar uma fonte nova e acrescentar uma linha aqui,
sem tocar em endpoint, schema ou cadeia. E o que torna o Strategy util na
pratica — a troca acontece na composicao, nao no codigo que consome.
"""

import logging

from app.boundary.gateway.fake import FakeCalendarProvider, FakeConfirmationProvider
from app.boundary.gateway.fastflights import FastFlightsProvider
from app.boundary.gateway.travelpayouts import TravelpayoutsProvider
from app.config import Settings
from app.control.busca.cadeia import ConfirmationChain
from app.control.busca.portas import CalendarProvider, ConfirmationProvider

logger = logging.getLogger(__name__)


def build_calendar_provider(settings: Settings) -> CalendarProvider:
    if settings.use_fake_providers:
        logger.warning("USE_FAKE_PROVIDERS ligado: camada 1 FALSA, nenhum preco e real")
        return FakeCalendarProvider()
    return TravelpayoutsProvider(settings.travelpayouts_token)


def build_confirmation_chain(settings: Settings) -> ConfirmationChain:
    """Monta a cadeia da camada 2, na ordem de preferencia.

    A ordem importa: o mais barato e mais rapido primeiro. Quando houver uma
    fonte paga de reserva, ela entra ao final — so e acionada se a gratuita
    falhar, mantendo o custo perto de zero no caso normal.
    """
    providers: list[ConfirmationProvider] = []

    if settings.use_fake_providers:
        logger.warning("USE_FAKE_PROVIDERS ligado: camada 2 FALSA, nenhum preco e real")
        return ConfirmationChain([FakeConfirmationProvider()])

    if settings.fastflights_enabled:
        providers.append(FastFlightsProvider())
    else:
        # Chave de desligamento: quando o Google mudar o formato e a biblioteca
        # quebrar, basta FASTFLIGHTS_ENABLED=false no .env. Sem deploy.
        logger.warning("fast-flights desabilitado por configuracao: camada 2 degradada")

    # Espaco reservado para as alternativas previstas em docs/PLANO-DE-ACAO.md:
    #   if settings.serpapi_token:
    #       providers.append(SerpApiProvider(settings.serpapi_token))
    #   if settings.playwright_enabled:
    #       providers.append(PlaywrightProvider())

    return ConfirmationChain(providers)
