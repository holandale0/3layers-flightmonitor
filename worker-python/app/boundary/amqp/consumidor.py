"""Consumidor AMQP do worker — etapa E4.1.

# O que muda, e o que nao muda

Nao muda nada da coleta. As mesmas funcoes que o endpoint HTTP chama sao
chamadas aqui, com os mesmos providers vindos da mesma fabrica — inclusive os
falsos do E2E. O worker continua sendo um especialista burro; ele so passou a
ter uma segunda porta de entrada.

# Request/reply, e nao "publica e esquece"

O core espera a resposta. Cada pedido chega com `reply_to` e `correlation_id`, e
a resposta volta pela fila temporaria que o proprio pedido criou. E o padrao de
request/reply do AMQP, e e o que permite trocar o transporte sem mexer na
semantica do motor.

# Erro de fonte NAO e erro de mensagem

Uma distincao que decide o comportamento inteiro:

    fonte fora do ar      -> responde com o desfecho degradado/erro
    mensagem corrompida   -> rejeita SEM reenfileirar, e ela vai para a dead-letter

Reenfileirar mensagem que nunca vai ser processada cria o laco de veneno: ela
volta, falha, volta de novo, para sempre. E o core, do outro lado, so ve
timeout.
"""

import asyncio
import json
import logging

import aio_pika

from app.boundary.amqp.filas import (
    CABECALHO_FALHA,
    FILA_CALENDARIO,
    FILA_CONFIRMACAO,
    PREFETCH,
)
from app.composicao.busca import build_calendar_provider, build_confirmation_chain
from app.config import Settings
from app.control.busca.portas import ProviderError
from app.schemas import (
    CalendarSearchRequest,
    CalendarSearchResponse,
    ConfirmRequest,
)

logger = logging.getLogger(__name__)

#: Quantas vezes esperar a fila aparecer, e de quanto em quanto tempo.
#: Cobre o `docker compose up` em que o worker sobe antes do core.
TENTATIVAS_DE_FILA = 20
ESPERA_ENTRE_TENTATIVAS = 3.0


class ConsumidorDaBusca:
    """Escuta as duas filas de pedido e responde a quem perguntou."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._conexao: aio_pika.abc.AbstractRobustConnection | None = None
        self._tarefa: asyncio.Task | None = None

    def iniciar_em_paralelo(self) -> None:
        """Agenda a conexao sem segurar quem chamou."""
        self._tarefa = asyncio.create_task(self._iniciar_tolerando_falha())

    async def _iniciar_tolerando_falha(self) -> None:
        try:
            await self.iniciar()
        except asyncio.CancelledError:
            raise
        except Exception as e:
            logger.warning(
                "nao foi possivel consumir do broker (%s): o worker segue apenas por HTTP", e
            )

    async def iniciar(self) -> None:
        # Conexao "robusta": o aio-pika reconecta sozinho quando o broker cai ou
        # reinicia. Sem isso, um restart do RabbitMQ deixaria o worker vivo,
        # saudavel no /health e surdo — o pior estado possivel.
        self._conexao = await aio_pika.connect_robust(self._settings.amqp_url)

        await self._escutar(FILA_CALENDARIO, self._varrer)
        await self._escutar(FILA_CONFIRMACAO, self._confirmar)

        logger.info(
            "consumindo %s e %s", FILA_CALENDARIO, FILA_CONFIRMACAO
        )

    async def parar(self) -> None:
        if self._tarefa is not None and not self._tarefa.done():
            self._tarefa.cancel()
        if self._conexao is not None and not self._conexao.is_closed:
            await self._conexao.close()

    async def _escutar(self, nome_da_fila: str, tratador) -> None:
        """Passa a consumir a fila, esperando ela existir.

        NAO declara: o core e o dono da topologia, e declarar aqui com um
        argumento diferente derrubaria o canal. A consequencia e que, se o
        worker subir primeiro, a fila ainda nao existe — o caso normal num
        `docker compose up`, e nao uma excecao.

        Por isso a espera. Sem ela, a ordem de subida dos containers decidiria
        se o sistema funciona, o que e a pior forma de dependencia: intermitente
        e dependente da maquina.
        """
        canal, fila = await self._esperar_a_fila(nome_da_fila)
        await fila.consume(self._envelope(canal, tratador))

    async def _esperar_a_fila(self, nome: str):
        canal = await self._conexao.channel()
        await canal.set_qos(prefetch_count=PREFETCH)

        for tentativa in range(1, TENTATIVAS_DE_FILA + 1):
            try:
                return canal, await canal.get_queue(nome, ensure=True)
            except Exception:
                if tentativa == TENTATIVAS_DE_FILA:
                    raise
                logger.info(
                    "fila %s ainda nao existe (tentativa %d/%d); o core e quem a declara",
                    nome, tentativa, TENTATIVAS_DE_FILA,
                )
                # get_queue com ensure=True derruba o canal quando a fila nao
                # existe: e preciso um canal novo a cada tentativa.
                canal = await self._conexao.channel()
                await canal.set_qos(prefetch_count=PREFETCH)
                await asyncio.sleep(ESPERA_ENTRE_TENTATIVAS)
        raise RuntimeError(f"fila {nome} nao apareceu")

    def _envelope(self, canal, tratador):
        """Trata uma mensagem: desserializa, processa e responde."""

        async def processar(mensagem: aio_pika.abc.AbstractIncomingMessage) -> None:
            try:
                pedido = json.loads(mensagem.body)
            except (ValueError, TypeError) as e:
                # Corpo ilegivel nunca vai melhorar com retentativa.
                logger.error("mensagem ilegivel, mandando para a dead-letter: %s", e)
                await mensagem.reject(requeue=False)
                return

            try:
                resposta, falha_da_fonte = await tratador(pedido)
            except Exception:
                # Aqui e defeito NOSSO, e nao da fonte: os tratadores ja
                # convertem falha de fonte em resposta. Reenfileirar repetiria o
                # mesmo defeito para sempre.
                logger.exception("falha ao processar mensagem; indo para a dead-letter")
                await mensagem.reject(requeue=False)
                return

            await self._responder(canal, mensagem, resposta, falha_da_fonte)
            await mensagem.ack()

        return processar

    async def _responder(self, canal, mensagem, resposta, falha_da_fonte=None) -> None:
        if not mensagem.reply_to:
            # Sem reply_to nao ha o que responder. Nao e erro: alguem pode ter
            # publicado o pedido a mao, para testar.
            logger.warning("pedido sem reply_to; resposta descartada")
            return

        cabecalhos = {}
        if falha_da_fonte:
            # O equivalente do HTTP 502 aqui. Sem ele, o core leria a resposta
            # vazia como "a janela nao tem oferta" e registraria a busca como
            # bem-sucedida — perdendo a distincao que o REST preserva.
            cabecalhos[CABECALHO_FALHA] = falha_da_fonte

        await canal.default_exchange.publish(
            aio_pika.Message(
                body=resposta.model_dump_json().encode(),
                content_type="application/json",
                headers=cabecalhos,
                correlation_id=mensagem.correlation_id,
                # SEM cabecalho de tipo, de proposito.
                #
                # A primeira versao mandava `__TypeId__` com o nome da classe
                # Python, e o Spring AMQP tentava resolver uma classe Java com
                # aquele nome — falhando com "not in the trusted packages".
                #
                # A correcao nao foi liberar o pacote: foi tirar o cabecalho.
                # Quem sabe em que tipo converter e QUEM RECEBE. Fazer o worker
                # anunciar nomes de classe do outro lado seria acoplamento na
                # direcao errada — o Python passaria a depender do desenho
                # interno do Java para responder.
            ),
            routing_key=mensagem.reply_to,
        )

    # ------------------------------------------------------------ camada 1

    async def _varrer(self, pedido: dict):
        """Devolve `(resposta, falha_da_fonte)`.

        O segundo item e o que separa "a fonte caiu" de "a janela esta vazia".
        Sem ele, os dois desfechos chegariam ao core como a mesma resposta
        vazia, e um monitor cuja fonte esta fora do ar voltaria a fila no
        intervalo normal em vez de retentar.
        """
        req = CalendarSearchRequest(**pedido)
        provider = build_calendar_provider(self._settings)

        try:
            resposta = await provider.buscar(req)
        except (ProviderError, ValueError) as e:
            logger.warning("varredura falhou: %s", e)
            vazia = CalendarSearchResponse(
                origin=req.origin, destination=req.destination, returned=0, kept=0
            )
            return vazia, str(e)

        logger.info(
            "%s->%s: %d ofertas recebidas, %d dentro dos criterios",
            req.origin, req.destination, resposta.returned, resposta.kept,
        )
        return resposta, None

    # ------------------------------------------------------------ camada 2

    async def _confirmar(self, pedido: dict):
        req = ConfirmRequest(**pedido)
        cadeia = build_confirmation_chain(self._settings)

        # A cadeia ja transforma queda de fonte em `degraded=True`; nao ha
        # excecao a tratar aqui.
        resposta = await cadeia.confirm(req)

        logger.info(
            "confirmacao %s->%s em %s: confirmado=%s degradado=%s",
            req.origin, req.destination, req.departure_date,
            resposta.confirmed, resposta.degraded,
        )
        # A camada 2 nunca sinaliza falha de fonte: queda ja vira degraded=True,
        # e o core sabe o que fazer com isso.
        return resposta, None


async def iniciar_em_segundo_plano(settings: Settings) -> ConsumidorDaBusca | None:
    """Sobe o consumidor SEM bloquear a subida do worker.

    <p>Em segundo plano de verdade, e nao com um timeout de espera. A primeira
    versao usava `wait_for(..., timeout=15)`, e o resultado foi o pior de dois
    mundos: a espera pela topologia pode levar um minuto — o core e quem declara
    as filas, e ele sobe depois —, entao o guarda de 15s cancelava justamente a
    espera que existia para dar certo. O worker subia "saudavel" e surdo, e o
    outro lado so via timeout.

    <p>Com a tarefa em segundo plano, o HTTP responde na hora e o consumo entra
    quando puder. Se o broker nunca aparecer, o worker segue servindo por HTTP —
    derrubar o processo por causa do transporte alternativo seria trocar uma
    degradacao por uma queda.
    """
    if not settings.deve_consumir_da_fila:
        return None

    consumidor = ConsumidorDaBusca(settings)
    consumidor.iniciar_em_paralelo()
    return consumidor
