"""Endpoints de busca de preco."""

import logging

from fastapi import APIRouter, HTTPException, status

from app.composicao.busca import build_calendar_provider, build_confirmation_chain
from app.config import get_settings
from app.control.busca.portas import ProviderError
from app.schemas import (
    CalendarSearchRequest,
    CalendarSearchResponse,
    ConfirmRequest,
    ConfirmResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/search", tags=["search"])


@router.post(
    "/calendar",
    response_model=CalendarSearchResponse,
    summary="Camada 1: varre uma janela de datas",
)
async def buscar_calendario(req: CalendarSearchRequest) -> CalendarSearchResponse:
    settings = get_settings()

    if not settings.use_fake_providers and not settings.travelpayouts_configured:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="TRAVELPAYOUTS_TOKEN nao configurado no worker",
        )

    # Pela fabrica, e nao instanciando a fonte aqui: e o que permite trocar a
    # camada 1 por uma fonte falsa no E2E entre servicos (E1.16) sem que este
    # modulo saiba que existe teste. Antes o Strategy da camada 1 existia so no
    # papel — a fabrica tinha a funcao, e ninguem a chamava.
    provider = build_calendar_provider(settings)

    try:
        resposta = await provider.buscar(req)
    except ValueError as e:
        # Janela incoerente: erro do chamador.
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e)) from e
    except ProviderError as e:
        # Falha da fonte externa. 502 deixa claro para o core-java que o
        # problema nao e do worker, e que ele deve marcar a busca como FAILED.
        logger.warning("camada 1 indisponivel: %s", e)
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e)) from e

    logger.info(
        "%s->%s: %d ofertas recebidas, %d dentro dos criterios",
        req.origin,
        req.destination,
        resposta.returned,
        resposta.kept,
    )
    return resposta


@router.post(
    "/confirm",
    response_model=ConfirmResponse,
    summary="Camada 2: confirma um candidato com dados reais de voo",
)
async def confirmar(req: ConfirmRequest) -> ConfirmResponse:
    """Confirma uma data especifica antes de o core-java disparar um alerta.

    Nunca devolve erro por falha de fonte. Se todas as fontes cairem, a resposta
    vem com `degraded=true` e o core decide o que fazer — tipicamente alertar
    mesmo assim, sem detalhe de voo. Derrubar a varredura por causa de uma
    camada opcional seria pior do que seguir sem ela.
    """
    cadeia = build_confirmation_chain(get_settings())
    resposta = await cadeia.confirm(req)

    logger.info(
        "confirmacao %s->%s em %s: confirmado=%s degradado=%s via=%s",
        req.origin,
        req.destination,
        req.departure_date,
        resposta.confirmed,
        resposta.degraded,
        resposta.provider,
    )
    return resposta
