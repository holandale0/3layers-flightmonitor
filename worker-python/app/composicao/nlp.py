"""Montagem da cadeia de interpretacao — etapa E3.1.

Ponto unico de registro, como a `providers/factory.py` da coleta. Trocar de
modelo, ou adicionar um segundo, e acrescentar uma linha aqui.
"""

import logging

from app.boundary.gateway.claude import ClaudeIntentProvider
from app.config import Settings
from app.control.nlp.cadeia import IntentChain
from app.control.nlp.portas import IntentProvider
from app.control.nlp.regras import RegrasIntentProvider

logger = logging.getLogger(__name__)


def build_intent_chain(settings: Settings) -> IntentChain:
    """Modelo primeiro, regras sempre por ultimo.

    A ordem nao e negociavel: o modelo entende mais, e as regras existem para
    que exista resposta quando ele nao esta disponivel. Inverter faria a chamada
    paga acontecer so quando ela nao fosse necessaria.

    <p>As regras <b>sempre</b> entram na lista, mesmo com o modelo configurado.
    E o que garante que a cadeia nunca fique vazia — e que uma chave revogada
    vire uma resposta mais pobre, e nao um erro 500.
    """
    providers: list[IntentProvider] = []

    if settings.anthropic_configured:
        providers.append(ClaudeIntentProvider(settings.anthropic_api_key, settings.anthropic_model))
    else:
        logger.info(
            "ANTHROPIC_API_KEY nao configurada: interpretacao apenas por regras (etapa E3.1)"
        )

    providers.append(RegrasIntentProvider())

    return IntentChain(providers)
