"""Interpretacao deterministica de pedidos em portugues — etapa E3.1.

# Por que existir, tendo LLM

Tres motivos, e nenhum deles e economia:

1. **E a rede de seguranca.** Quando a chave nao esta configurada, quando a API
   cai ou quando a cota acaba, isto continua respondendo. Mesma politica da
   camada 2 de coleta: degradar, e nao morrer;
2. **E o teste do resto.** Um teste que depende de LLM e caro, lento e
   nao-deterministico — a mesma frase pode virar duas respostas. Aqui a mesma
   entrada da sempre a mesma saida, entao o contrato de `MonitorIntent` fica
   coberto de verdade;
3. **Cobre o jeito comum de pedir.** "Quero ir pra Lisboa em marco por ate
   4 mil" nao precisa de modelo nenhum, e e assim que a maioria dos pedidos vai
   chegar.

# O que ele NAO faz

Frase torta, pedido com ressalva, ironia, varias viagens no mesmo texto. Para
isso serve o modelo. Quando nao entende, **diz que nao entendeu** em vez de
adivinhar.
"""

import logging
import re
import unicodedata
from calendar import monthrange
from datetime import date, timedelta
from decimal import Decimal

from app.control.nlp.aeroportos import procurar_no_texto, resolver
from app.schemas import IntentRequest, MonitorIntent

logger = logging.getLogger(__name__)

NOME = "regras"

MESES = {
    "janeiro": 1, "fevereiro": 2, "marco": 3, "abril": 4, "maio": 5, "junho": 6,
    "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10, "novembro": 11,
    "dezembro": 12,
}

#: Palavras que indicam ORIGEM quando aparecem antes da cidade.
MARCAS_DE_ORIGEM = ("de", "do", "da", "desde", "saindo de", "partindo de", "sair de")

#: Palavras que indicam DESTINO.
MARCAS_DE_DESTINO = ("para", "pra", "pro", "ate", "rumo a", "com destino a", "a")


def _sem_acento(texto: str) -> str:
    return "".join(
        c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn"
    ).lower()


class RegrasIntentProvider:
    """Extrai a intencao por padroes de escrita, sem modelo."""

    name = NOME

    async def interpretar(self, req: IntentRequest) -> MonitorIntent:
        texto = _sem_acento(req.texto)
        hoje = req.hoje or date.today()

        avisos: list[str] = []
        origem, destino = self._rota(texto, req, avisos)
        de, ate = self._janela(texto, hoje, avisos)
        permanencia = self._permanencia(texto)
        escalas, direto = self._escalas(texto)

        intent = MonitorIntent(
            origin=origem,
            destination=destino,
            departure_from=de,
            departure_to=ate,
            min_stay_days=permanencia[0],
            max_stay_days=permanencia[1],
            max_price=self._preco(texto, avisos),
            max_stops=escalas,
            prefere_voo_direto=direto,
            passengers=self._passageiros(texto),
            avoided_airlines=self._companhias_evitadas(req.texto),
            label=self._rotulo(destino),
            provider=NOME,
            avisos=avisos,
        )

        intent.confianca = self._confianca(intent)
        for faltou in intent.faltando():
            intent.avisos.append(f"nao consegui identificar: {faltou}")

        return intent

    # ------------------------------------------------------------- rota

    def _rota(
        self, texto: str, req: IntentRequest, avisos: list[str]
    ) -> tuple[str | None, str | None]:
        cidades = procurar_no_texto(texto)

        if not cidades:
            return resolver(req.origem_padrao), None

        if len(cidades) == 1:
            # Uma cidade so e quase sempre o destino: "quero ir pra Lisboa".
            # A origem vem do padrao — normalmente a cidade de quem usa.
            destino = cidades[0][0]
            origem = resolver(req.origem_padrao)
            if not origem:
                avisos.append(
                    "so identifiquei o destino; informe a origem ou configure uma padrao"
                )
            return origem, destino

        # Duas ou mais: a marca antes de cada nome decide quem e quem.
        origem = self._por_marca(texto, cidades, MARCAS_DE_ORIGEM)
        destino = self._por_marca(texto, cidades, MARCAS_DE_DESTINO)

        if origem and destino and origem != destino:
            return origem, destino

        # Sem marca clara, a ordem resolve: "Sao Paulo Lisboa" e origem-destino.
        if len(cidades) >= 2 and cidades[0][0] != cidades[1][0]:
            return cidades[0][0], cidades[1][0]

        avisos.append("citou mais de uma cidade e nao ficou claro qual e a origem")
        return None, None

    def _por_marca(
        self, texto: str, cidades: list[tuple[str, str, int]], marcas: tuple[str, ...]
    ) -> str | None:
        """Cidade precedida por uma das marcas, a mais proxima primeiro."""
        melhor: tuple[int, str] | None = None

        for codigo, _nome, pos in cidades:
            antes = texto[max(0, pos - 20):pos]
            for marca in marcas:
                if re.search(rf"(?<![a-z]){re.escape(marca)}\s+$", antes):
                    distancia = len(antes) - antes.rfind(marca)
                    if melhor is None or distancia < melhor[0]:
                        melhor = (distancia, codigo)
        return melhor[1] if melhor else None

    # ----------------------------------------------------------- janela

    def _janela(
        self, texto: str, hoje: date, avisos: list[str]
    ) -> tuple[date | None, date | None]:
        # "entre 10 e 20 de marco"
        m = re.search(
            rf"entre\s+(\d{{1,2}})\s+e\s+(\d{{1,2}})\s+de\s+({'|'.join(MESES)})", texto
        )
        if m:
            mes = MESES[m.group(3)]
            ano = self._ano_do_mes(mes, hoje, texto)
            return date(ano, mes, int(m.group(1))), date(ano, mes, int(m.group(2)))

        # "em marco", "em marco de 2027", "marco"
        m = re.search(rf"(?<![a-z])({'|'.join(MESES)})(?![a-z])", texto)
        if m:
            mes = MESES[m.group(1)]
            ano = self._ano_do_mes(mes, hoje, texto)
            return date(ano, mes, 1), date(ano, mes, monthrange(ano, mes)[1])

        # "daqui a 3 meses"
        m = re.search(r"daqui a (\d{1,2}) (mes|meses)", texto)
        if m:
            inicio = hoje + timedelta(days=30 * int(m.group(1)))
            return inicio, inicio + timedelta(days=30)

        # "nas ferias de julho" ja caiu no caso do mes; "no fim do ano":
        if "fim do ano" in texto or "final do ano" in texto:
            return date(hoje.year, 12, 1), date(hoje.year, 12, 31)

        avisos.append("nao encontrei o periodo da viagem no texto")
        return None, None

    def _ano_do_mes(self, mes: int, hoje: date, texto: str) -> int:
        """Ano explicito vence; senao, a proxima ocorrencia do mes.

        Sem esta regra, "em marco" dito em abril criaria um monitor para uma
        data que ja passou — e o banco recusaria, com um erro que nao explica
        nada a quem escreveu a frase.
        """
        m = re.search(r"(?<!\d)(20\d{2})(?!\d)", texto)
        if m:
            return int(m.group(1))
        return hoje.year if mes >= hoje.month else hoje.year + 1

    # ------------------------------------------------------------ preco

    #: Um valor em reais: "1.500", "1500" ou "1.500,00".
    #:
    #: O `(?!\d)` no fim nao e detalhe. Sem ele, `\d{1,3}` casava com "250" de
    #: "2500" e o pedido de R$ 2.500 virava um monitor de R$ 250 — que nunca
    #: alertaria, e o usuario nao teria como saber por que.
    VALOR = r"(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?(?!\d)"

    def _preco(self, texto: str, avisos: list[str]) -> Decimal | None:
        # "ate 4 mil", "por 4 mil", "4 mil reais"
        m = re.search(r"(?:ate|por|maximo de|no maximo)?\s*(?:r\$\s*)?(\d{1,3})\s*mil", texto)
        if m:
            return Decimal(m.group(1)) * 1000

        # "ate R$ 1.500", "no maximo 2500"
        m = re.search(rf"(?:ate|por|maximo de|no maximo|r\$)\s*(?:r\$\s*)?{self.VALOR}", texto)
        if m:
            return self._numero(m)

        # "2500 reais"
        m = re.search(rf"{self.VALOR}\s*(?:reais|conto)", texto)
        if m:
            return self._numero(m)

        avisos.append("nao encontrei o preco maximo no texto")
        return None

    def _numero(self, m: re.Match[str]) -> Decimal:
        inteiro = m.group(1).replace(".", "")
        centavos = m.group(2) or "00"
        return Decimal(f"{inteiro}.{centavos}")

    # ------------------------------------------------------ permanencia

    def _permanencia(self, texto: str) -> tuple[int | None, int | None]:
        if re.search(r"(uma|1) semana", texto):
            return 7, 9
        if re.search(r"(duas|2) semanas", texto):
            return 14, 16
        if re.search(r"(um|1) mes(?!es)", texto):
            return 28, 32

        m = re.search(r"(?:por|ficar|durante)\s+(\d{1,2})\s+dias", texto)
        if m:
            dias = int(m.group(1))
            # Uma folga de dois dias para cada lado: quem diz "10 dias" aceita
            # 9 ou 11 se a passagem for melhor, e recusar isso descartaria
            # oferta boa por um detalhe que a pessoa nao quis dizer com rigor.
            return max(1, dias - 1), dias + 2

        m = re.search(r"(\d{1,2})\s+a\s+(\d{1,2})\s+dias", texto)
        if m:
            return int(m.group(1)), int(m.group(2))

        return None, None

    # --------------------------------------------------------- escalas

    def _escalas(self, texto: str) -> tuple[int | None, bool]:
        if re.search(r"(voo )?direto|sem escala|sem conexao|nao quero escala", texto):
            # Preferencia, e nao exigencia: `prefere_voo_direto` penaliza a
            # escala na nota. Quem quer excluir de verdade diz "somente direto".
            if re.search(r"(so|somente|apenas|obrigatoriamente) (voo )?direto", texto):
                return 0, True
            return None, True

        m = re.search(r"(?:no maximo|ate)\s+(\d)\s+escalas?", texto)
        if m:
            return int(m.group(1)), False

        if re.search(r"(uma|1) escala", texto):
            return 1, False

        return None, False

    # ------------------------------------------------------ passageiros

    def _passageiros(self, texto: str) -> int | None:
        if re.search(r"casal|nos dois|duas pessoas|2 pessoas", texto):
            return 2
        m = re.search(r"(?:para|somos)\s+(\d)\s+(?:pessoas|adultos|passageiros)", texto)
        if m:
            return int(m.group(1))
        return None

    def _companhias_evitadas(self, texto_original: str) -> list[str]:
        achadas: list[str] = []
        for m in re.finditer(
            r"(?:evitar|sem|nao quero|menos)\s+(?:a\s+|voar\s+(?:pela|com)\s+)?"
            r"([A-Z][\wÀ-ÿ]{2,}(?:\s+[A-Z][\wÀ-ÿ]+)?)",
            texto_original,
        ):
            achadas.append(m.group(1).strip())
        return achadas

    def _rotulo(self, destino: str | None) -> str | None:
        return f"Viagem para {destino}" if destino else None

    # -------------------------------------------------------- confianca

    def _confianca(self, intent: MonitorIntent) -> float:
        """Fracao dos cinco campos essenciais que foram encontrados.

        Nao mede se a interpretacao esta CERTA — mede o quanto dela existe. Um
        numero honesto e modesto vale mais do que uma certeza inventada.
        """
        essenciais = [
            intent.origin,
            intent.destination,
            intent.departure_from,
            intent.departure_to,
            intent.max_price,
        ]
        return round(sum(1 for c in essenciais if c) / len(essenciais), 2)
