"""Contrato da interpretacao de linguagem natural — etapa E3.1.

# Por que Strategy aqui tambem

Mesma razao da camada 2 de coleta (D-026): existem varias formas de transformar
texto em intencao, com custo e alcance diferentes, e a escolha pertence a
composicao — nao ao codigo que consome.

    regras     deterministico, gratuito, cobre o jeito comum de pedir
    Claude     entende o pedido torto, custa por chamada e depende de rede

O roteador nao sabe qual esta ativo. Quando o modelo cai, ou a chave nao esta
configurada, a interpretacao por regras continua respondendo — degradar, e nao
morrer, e a mesma politica da coleta.
"""

from typing import Protocol, runtime_checkable

from app.schemas import IntentRequest, MonitorIntent


class IntentError(RuntimeError):
    """Falha ao interpretar. Quem chama decide se degrada ou desiste."""

    def __init__(self, provider: str, mensagem: str) -> None:
        super().__init__(f"[{provider}] {mensagem}")
        self.provider = provider
        self.mensagem = mensagem


@runtime_checkable
class IntentProvider(Protocol):
    """Transforma texto livre em intencao de monitor.

    Implementacoes **nao inventam**: campo que nao deu para extrair volta nulo,
    com um aviso dizendo o que faltou. Um monitor criado a partir de chute
    vigiaria a rota errada por meses, em silencio — o pior desfecho possivel,
    porque parece que esta funcionando.
    """

    name: str

    async def interpretar(self, req: IntentRequest) -> MonitorIntent: ...
