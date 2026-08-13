"""Cadeia de confirmacao: Strategy com fallback e degradacao explicita.

O Strategy sozinho resolve "trocar de implementacao". Nao resolve o que
realmente ameaca este projeto: a implementacao escolhida quebrar em producao,
sem aviso, num domingo.

Por isso a cadeia:

    ConfirmationChain([fast-flights, <futuro serpapi>, <futuro playwright>])
              |
              +-- tenta o primeiro
              |     ok      -> devolve, registrando quem confirmou
              |     falhou  -> registra a falha e tenta o proximo
              |
              +-- todos falharam -> DEGRADADO, e nao erro

A diferenca entre "degradado" e "erro" e o coracao do desenho. Erro derrubaria
a varredura por causa de uma camada opcional. Degradado deixa o core-java
seguir gravando historico e alertando, apenas sem detalhe de voo.

Toda tentativa vira um `ProviderAttempt` na resposta — com duracao e motivo da
falha. Assim a queda de um provider aparece como dado observavel, e nao como
ausencia misteriosa de alertas.
"""

import logging
import time

from app.control.busca.portas import ConfirmationProvider, ProviderError
from app.schemas import ConfirmedOffer, ConfirmRequest, ConfirmResponse, ProviderAttempt

logger = logging.getLogger(__name__)


class ConfirmationChain:

    def __init__(self, providers: list[ConfirmationProvider]) -> None:
        self._providers = providers

    @property
    def provider_names(self) -> list[str]:
        return [p.name for p in self._providers]

    async def confirm(self, req: ConfirmRequest) -> ConfirmResponse:
        resposta = ConfirmResponse()

        if not self._providers:
            resposta.degraded = True
            resposta.warnings.append(
                "nenhuma fonte de confirmacao habilitada: alertas sairao sem detalhe de voo"
            )
            return resposta

        for provider in self._providers:
            tentativa, oferta = await self._tentar(provider, req)
            resposta.attempts.append(tentativa)

            if not tentativa.ok:
                continue

            if oferta is None:
                # Consultamos com sucesso e nao ha voo assim. Nao adianta
                # perguntar a outra fonte a mesma coisa: a resposta e definitiva.
                resposta.confirmed = False
                resposta.provider = provider.name
                resposta.warnings.append(
                    f"{provider.name} nao encontrou voo real para estas datas: "
                    "o candidato da camada 1 nao se sustentou"
                )
                return resposta

            resposta.confirmed = True
            resposta.offer = oferta
            resposta.provider = provider.name
            self._avisar_divergencia(req, oferta, resposta)
            return resposta

        # Chegou aqui: nenhuma fonte respondeu.
        resposta.degraded = True
        resposta.warnings.append(
            "todas as fontes de confirmacao falharam: seguindo sem detalhe de voo"
        )
        logger.warning(
            "camada 2 degradada para %s->%s em %s: %s",
            req.origin,
            req.destination,
            req.departure_date,
            "; ".join(f"{a.provider}={a.error}" for a in resposta.attempts),
        )
        return resposta

    async def _tentar(
        self, provider: ConfirmationProvider, req: ConfirmRequest
    ) -> tuple[ProviderAttempt, ConfirmedOffer | None]:
        inicio = time.monotonic()
        try:
            oferta = await provider.confirm(req)
        except ProviderError as e:
            return (
                ProviderAttempt(
                    provider=provider.name,
                    ok=False,
                    error=e.mensagem,
                    duration_ms=self._ms(inicio),
                ),
                None,
            )
        except Exception as e:
            # Rede de seguranca: um provider mal comportado que deixe escapar
            # excecao propria nao pode derrubar a cadeia.
            logger.exception("provider %s levantou excecao inesperada", provider.name)
            return (
                ProviderAttempt(
                    provider=provider.name,
                    ok=False,
                    error=f"excecao nao tratada: {type(e).__name__}: {e}",
                    duration_ms=self._ms(inicio),
                ),
                None,
            )

        return (
            ProviderAttempt(
                provider=provider.name,
                ok=True,
                found=oferta is not None,
                duration_ms=self._ms(inicio),
            ),
            oferta,
        )

    @staticmethod
    def _avisar_divergencia(
        req: ConfirmRequest, oferta: ConfirmedOffer, resposta: ConfirmResponse
    ) -> None:
        """Mede o falso-positivo do cache (RISCO-003).

        A camada 1 serve preco cacheado. Saber o quanto ele diverge do preco
        real e o que permite calibrar a confianca nela ao longo do tempo.
        """
        # Divergencia de preco: so faz sentido se houver candidato para comparar.
        if req.candidate_price is not None and req.candidate_price > 0:
            diferenca = oferta.price - req.candidate_price
            percentual = (diferenca / req.candidate_price) * 100
            if abs(percentual) >= 5:
                sentido = "acima" if diferenca > 0 else "abaixo"
                resposta.warnings.append(
                    f"preco real {abs(percentual):.0f}% {sentido} do candidato da camada 1 "
                    f"({req.candidate_price} -> {oferta.price})"
                )

        # Aeroporto divergente: verificacao INDEPENDENTE da anterior. A camada 1
        # trabalha em nivel de cidade (RISCO-006), entao o voo real pode partir
        # de outro aeroporto da regiao — e isso precisa ser dito mesmo quando
        # nao ha preco candidato para comparar.
        if oferta.departure_airport and oferta.departure_airport.upper() != req.origin.upper():
            resposta.warnings.append(
                f"o voo real parte de {oferta.departure_airport}, e nao de {req.origin}"
            )

        # Confirmacao parcial: a fonte respondeu, mas nao trouxe tudo. Ja foi
        # observado o parser devolver hora ausente (time=[None, 45]). Sem este
        # aviso, o campo chegaria nulo ao banco sem ninguem perceber que a
        # camada 2 esta degradando aos poucos.
        faltando = [
            rotulo
            for rotulo, valor in (
                ("companhia", oferta.airline),
                ("horario de partida", oferta.departure_at),
                ("escalas", oferta.stops),
            )
            if valor is None
        ]
        if faltando:
            resposta.warnings.append(
                f"confirmado, mas a fonte nao trouxe: {', '.join(faltando)}"
            )

    @staticmethod
    def _ms(inicio: float) -> int:
        return int((time.monotonic() - inicio) * 1000)
