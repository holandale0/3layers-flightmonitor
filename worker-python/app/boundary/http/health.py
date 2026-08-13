"""Endpoints de saúde do worker."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.config import Settings, get_settings

router = APIRouter(tags=["health"])


class ProvidersStatus(BaseModel):
    """Prontidão de cada fonte de preço.

    Camada 1 (travelpayouts) faz a varredura ampla; camada 2 (fast_flights)
    confirma o candidato. Ver docs/PLANO-DE-ACAO.md secao 4.
    """

    travelpayouts: bool
    fast_flights: bool


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    providers: ProvidersStatus


@router.get("/health", response_model=HealthResponse, summary="Saúde do worker")
def health() -> HealthResponse:
    settings: Settings = get_settings()
    return HealthResponse(
        status="UP",
        service=settings.service_name,
        version=settings.version,
        providers=ProvidersStatus(
            travelpayouts=settings.travelpayouts_configured,
            fast_flights=settings.fastflights_enabled,
        ),
    )
