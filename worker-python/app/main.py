"""Worker de busca de preços do Flight Monitor.

Especialista burro por design: recebe rota + data, devolve preço. Não conhece
monitores, não conhece destinatários e não decide se um preço é bom — isso é
responsabilidade do core-java. Ver docs/PLANO-DE-ACAO.md secao 3.
"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.boundary.amqp.consumidor import iniciar_em_segundo_plano
from app.boundary.http import canario, health, nlp, search
from app.config import get_settings

settings = get_settings()

logging.basicConfig(
    level=settings.log_level,
    format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    logger.info("%s v%s iniciado", settings.service_name, settings.version)
    if not settings.travelpayouts_configured:
        # Mensagens de log ficam em ASCII: o console do Windows usa cp1252 por
        # padrao e corrompe acentos e travessoes. Ver BUG-001 em docs/BUGS.md.
        logger.warning(
            "TRAVELPAYOUTS_TOKEN nao configurado - camada 1 da coleta indisponivel (etapa E1.5)"
        )
    # Segunda porta de entrada, ao lado do HTTP (E4.1). Nao impede a subida se
    # o broker estiver fora: melhor servir por HTTP do que nao servir.
    consumidor = await iniciar_em_segundo_plano(settings)

    yield

    if consumidor is not None:
        await consumidor.parar()
    logger.info("%s encerrado", settings.service_name)


app = FastAPI(
    title="Flight Monitor Worker",
    description="Busca e análise de preços de passagens aéreas.",
    version=settings.version,
    lifespan=lifespan,
)

app.include_router(health.router)
app.include_router(search.router)
app.include_router(nlp.router)
app.include_router(canario.router)
