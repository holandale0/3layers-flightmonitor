"""Endpoint do canario — etapa E4.5.

O worker e quem conhece as fontes, entao e ele quem as sonda. Ele **nao decide**
o que fazer com o resultado: reporta o que viu, e quem agenda, guarda e alarma e
o core-java (regra 2 da secao 3 do PLANO-DE-ACAO).
"""

import logging

from fastapi import APIRouter, Depends

from app.composicao.busca import build_calendar_provider, build_confirmation_chain
from app.config import Settings, get_settings
from app.control.canario.sonda import sondar_camada1, sondar_camada2

log = logging.getLogger(__name__)

router = APIRouter(tags=["canario"])


@router.get("/canario")
async def canario(settings: Settings = Depends(get_settings)) -> dict:
    """Consulta as duas camadas de verdade e devolve o que achou.

    Devolve **200 mesmo com problema**, de proposito: o resultado do canario e
    dado, e nao erro de requisicao. Quem chamou conseguiu o que pediu — a
    resposta e que traz a ma noticia. Usar 5xx aqui faria um proxy no meio do
    caminho transformar diagnostico em falha de rede.
    """
    camada1 = await sondar_camada1(build_calendar_provider(settings))
    camada2 = await sondar_camada2(build_confirmation_chain(settings))

    saudavel = camada1.formato_ok and camada2.formato_ok

    if not saudavel:
        # ERROR e nao WARN: se o formato mudou, o sistema vai parar de alertar,
        # e isso e a falha mais grave que este projeto tem.
        log.error(
            "canario detectou mudanca de formato nas fontes: camada1=%s camada2=%s",
            camada1.para_dict(), camada2.para_dict())

    return {
        "saudavel": saudavel,
        "camadas": [camada1.para_dict(), camada2.para_dict()],
    }
