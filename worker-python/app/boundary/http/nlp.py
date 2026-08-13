"""Endpoint de linguagem natural — etapa E3.1."""

import logging

from fastapi import APIRouter

from app.composicao.nlp import build_intent_chain
from app.config import get_settings
from app.schemas import IntentRequest, MonitorIntent

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/nlp", tags=["nlp"])


@router.post(
    "/intent",
    response_model=MonitorIntent,
    summary="Transforma um pedido em texto livre numa intencao de monitor",
)
async def interpretar(req: IntentRequest) -> MonitorIntent:
    """Interpreta, e nao cria nada.

    A criacao do monitor e da etapa E3.2, e fica no core-java — que e o dono do
    banco (regra 1). Aqui a resposta e so a leitura do pedido, com o que faltou
    dito em voz alta.

    <p>Nunca devolve erro por falha de interpretacao: a cadeia sempre termina
    nas regras, que nao dependem de rede nem de chave. O pior caso e uma
    intencao mais pobre, com `provider` e `avisos` explicando o que houve.
    """
    cadeia = build_intent_chain(get_settings())
    intent = await cadeia.interpretar(req)

    logger.info(
        "intencao interpretada por %s: %s->%s, confianca %.2f, %d aviso(s)",
        intent.provider,
        intent.origin,
        intent.destination,
        intent.confianca,
        len(intent.avisos),
    )
    return intent
