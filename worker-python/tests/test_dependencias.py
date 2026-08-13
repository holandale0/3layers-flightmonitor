"""Tudo que o codigo importa esta declarado no requirements.txt.

# Por que isto existe

Nasceu do BUG-014. `fast-flights` — a camada 2 inteira da coleta — ficou
COMENTADA no requirements.txt da etapa E1.6 ate a E4.2. Ninguem percebeu por
seis semanas porque a venv local tinha a biblioteca instalada a mao: na maquina
de quem desenvolve funcionava, e o arquivo que descreve o ambiente mentia.

So apareceu quando o worker subiu em container. E o modo de falha era discreto:
nao houve erro de importacao no boot: a cadeia de providers apenas registrava
que a camada 2 estava indisponivel, e o sistema — corretamente — se recusava a
alertar com preco nao confirmado. Ou seja, o sintoma era **o monitor parar de
avisar**, sem nada vermelho em lugar nenhum.

Este teste transforma "instalei na minha maquina" em erro de build.
"""

import ast
import pathlib
import sys

RAIZ = pathlib.Path(__file__).resolve().parents[1]
APP = RAIZ / "app"
REQUIREMENTS = RAIZ / "requirements.txt"

# Nome do modulo que se importa != nome do pacote que se instala. Sao poucos e
# conhecidos; um mapa explicito e melhor que adivinhacao por heuristica.
MODULO_PARA_PACOTE = {
    "fast_flights": "fast-flights",
    "pydantic_settings": "pydantic-settings",
    "aio_pika": "aio-pika",
    "dotenv": "python-dotenv",
    "anthropic": "anthropic",
}


def _pacotes_declarados() -> set[str]:
    """Os pacotes do requirements.txt, ignorando comentarios."""
    declarados = set()
    for linha in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        linha = linha.strip()
        if not linha or linha.startswith("#"):
            continue
        # Separa "pacote==1.2.3", "pacote>=1.2" e "pacote[extra]==1.2.3"
        nome = linha.split("==")[0].split(">=")[0].split("<")[0].split("[")[0]
        declarados.add(nome.strip().lower())
    return declarados


def _modulos_de_terceiros() -> dict[str, str]:
    """Os pacotes de terceiros importados pelo codigo -> onde foram vistos."""
    achados: dict[str, str] = {}

    for arquivo in APP.rglob("*.py"):
        if "__pycache__" in arquivo.parts:
            continue
        arvore = ast.parse(arquivo.read_text(encoding="utf-8"), filename=str(arquivo))

        for no in ast.walk(arvore):
            nomes: list[str] = []
            if isinstance(no, ast.Import):
                nomes = [a.name.split(".")[0] for a in no.names]
            elif isinstance(no, ast.ImportFrom):
                # `from . import x` tem level > 0 e module None: e import
                # relativo, nunca de terceiro.
                if no.level == 0 and no.module:
                    nomes = [no.module.split(".")[0]]

            for nome in nomes:
                if nome == "app" or nome in sys.stdlib_module_names:
                    continue
                achados.setdefault(nome, str(arquivo.relative_to(RAIZ)))

    return achados


def test_todo_import_de_terceiro_esta_no_requirements():
    """O arquivo que descreve o ambiente tem que descrever o ambiente.

    Se este teste falhar, o worker sobe numa maquina limpa SEM a biblioteca — e
    o sintoma nao vai ser um erro claro, e sim o monitor deixando de avisar.
    """
    declarados = _pacotes_declarados()
    faltando = []

    for modulo, onde in sorted(_modulos_de_terceiros().items()):
        pacote = MODULO_PARA_PACOTE.get(modulo, modulo).lower()
        if pacote not in declarados and pacote.replace("_", "-") not in declarados:
            faltando.append(f"{modulo} (importado em {onde}) -> falta '{pacote}'")

    assert not faltando, (
        "importados pelo codigo e ausentes do requirements.txt:\n  "
        + "\n  ".join(faltando)
    )


def test_fast_flights_esta_declarado():
    """A camada 2 especificamente, porque foi ela que faltou por seis semanas.

    O teste acima ja cobre o caso geral. Este existe para que, se alguem
    comentar a linha de novo, a falha diga **o nome do que quebrou** em vez de
    apenas "um pacote esta faltando".
    """
    assert "fast-flights" in _pacotes_declarados(), (
        "fast-flights e a camada 2 da coleta: sem ela o sistema nao confirma "
        "preco e para de alertar, silenciosamente (BUG-014)"
    )


def test_o_transporte_liga_por_qualquer_uma_das_duas_chaves():
    """WORKER_TRANSPORTE=AMQP tem que ligar o consumidor, como AMQP_ENABLED.

    A E4.2 tropecou nisto: o compose passava WORKER_TRANSPORTE para os dois
    servicos, e o worker — que so conhecia AMQP_ENABLED — subia sem consumidor.
    As filas apareciam no RabbitMQ com `consumers = 0`, e uma busca por fila
    esperaria para sempre. Falha silenciosa, do pior tipo: tudo "no ar".
    """
    from app.config import Settings

    assert Settings(worker_transporte="AMQP").deve_consumir_da_fila
    assert Settings(worker_transporte="amqp").deve_consumir_da_fila
    assert Settings(amqp_enabled=True).deve_consumir_da_fila

    # E o padrao continua sendo nao consumir: quem manda no transporte e o core.
    assert not Settings().deve_consumir_da_fila
    assert not Settings(worker_transporte="REST").deve_consumir_da_fila
