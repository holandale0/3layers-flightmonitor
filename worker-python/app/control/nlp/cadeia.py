"""Cadeia de interpretacao, com degradacao — etapa E3.1.

Mesmo desenho da `ConfirmationChain` da coleta: tenta na ordem de preferencia e,
se a primeira cair, usa a seguinte. A diferenca e que aqui **sempre ha uma
resposta** — as regras nao dependem de rede nem de chave, entao nunca existe o
desfecho "nao consegui nem tentar".

Isso muda o contrato para melhor: quem chama nunca precisa tratar indisponi-
bilidade. O pior caso e uma interpretacao mais pobre, com `provider: "regras"`
na resposta dizendo exatamente isso.
"""

import logging

from app.control.nlp.portas import IntentError, IntentProvider
from app.schemas import IntentRequest, MonitorIntent

logger = logging.getLogger(__name__)


class IntentChain:

    def __init__(self, providers: list[IntentProvider]) -> None:
        self._providers = providers

    @property
    def provider_names(self) -> list[str]:
        return [p.name for p in self._providers]

    async def interpretar(self, req: IntentRequest) -> MonitorIntent:
        ultimo_erro: str | None = None

        for provider in self._providers:
            try:
                intent = await provider.interpretar(req)
            except IntentError as e:
                logger.warning("provider %s falhou: %s", provider.name, e.mensagem)
                ultimo_erro = e.mensagem
                continue
            except Exception as e:
                # Rede de seguranca: um provider mal comportado nao pode
                # derrubar a interpretacao inteira.
                logger.exception("provider %s levantou excecao inesperada", provider.name)
                ultimo_erro = f"{type(e).__name__}: {e}"
                continue

            if ultimo_erro:
                # O usuario merece saber que a resposta veio do plano B — a
                # interpretacao por regras entende menos, e isso explica um
                # resultado mais pobre do que o esperado.
                intent.avisos.append(
                    f"interpretado por {provider.name}: a opcao preferida falhou ({ultimo_erro})"
                )
            return intent

        # So chega aqui se a lista estiver vazia, o que a fabrica nao permite.
        raise IntentError("cadeia", ultimo_erro or "nenhum interpretador disponivel")
