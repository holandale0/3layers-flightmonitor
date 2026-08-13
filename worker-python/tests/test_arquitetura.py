"""As camadas do worker, verificadas como teste.

Espelha o `ArquiteturaTest` do core-java. Sem isto, a organizacao em pastas e
so convencao — e convencao que ninguem verifica dura ate a primeira pressa.

# As camadas aqui

    boundary/   as bordas, nos DOIS sentidos:
                  http/ e amqp/  entrada
                  gateway/       saida (fontes externas, modelo de linguagem)
    control/    a logica: as PORTAS e as cadeias que decidem quem chamar
    composicao/ a raiz de composicao: o unico lugar que conhece os dois lados

# Nao ha entity, e isso e proposital

O worker e stateless por desenho: o Java e o dono do banco (regra 1 da secao 3
do PLANO-DE-ACAO). A ausencia da camada e informacao, e nao esquecimento — e
por isso ela esta escrita aqui, e nao apenas ausente.
"""

import ast
import pathlib

APP = pathlib.Path(__file__).resolve().parents[1] / "app"


def _modulos(pacote: str) -> list[pathlib.Path]:
    return [p for p in (APP / pacote).rglob("*.py") if "__pycache__" not in p.parts]


def _importa(arquivo: pathlib.Path) -> set[str]:
    """Os modulos `app.*` que este arquivo importa."""
    arvore = ast.parse(arquivo.read_text(encoding="utf-8"), filename=str(arquivo))
    achados: set[str] = set()

    for no in ast.walk(arvore):
        if isinstance(no, ast.ImportFrom) and no.module and no.module.startswith("app."):
            achados.add(no.module)
        elif isinstance(no, ast.Import):
            for alias in no.names:
                if alias.name.startswith("app."):
                    achados.add(alias.name)
    return achados


def _violacoes(pacote: str, proibido: str) -> list[str]:
    encontradas = []
    for arquivo in _modulos(pacote):
        for importado in _importa(arquivo):
            if importado.startswith(f"app.{proibido}"):
                encontradas.append(f"{arquivo.relative_to(APP)} importa {importado}")
    return encontradas


def test_controle_nao_conhece_a_borda():
    """O controle depende das PORTAS, e nunca de quem as implementa.

    E a mesma propriedade que permitiu, no core-java, trocar REST por
    mensageria sem tocar no motor: quem chama nao sabe quem atende.
    """
    assert _violacoes("control", "boundary") == []


def test_controle_nao_conhece_a_raiz_de_composicao():
    """Quem e escolhido nao decide a propria escolha.

    Se o controle importasse a composicao, a dependencia daria a volta: a
    cadeia passaria a saber que existe um provider falso, um modelo pago e uma
    chave de configuracao — tudo que ela existe para ignorar.
    """
    assert _violacoes("control", "composicao") == []


def test_gateway_nao_conhece_a_entrada():
    """Um adaptador de saida nao sabe quem pediu.

    O `travelpayouts` nao pode importar o router HTTP: se importasse, mudar de
    transporte passaria a mexer na fonte de precos — e a E4.1 mostrou que
    trocar transporte tem que ser barato.
    """
    assert _violacoes("boundary/gateway", "boundary.http") == []
    assert _violacoes("boundary/gateway", "boundary.amqp") == []


def test_entrada_nao_conhece_adaptador_direto():
    """HTTP e AMQP pedem pela raiz de composicao, e nao pelo adaptador.

    As duas portas de entrada fazem a MESMA coisa por caminhos diferentes. Se
    uma delas instanciasse o Travelpayouts direto, as duas deixariam de estar
    de acordo — e o E2E entre servicos so pegaria por sorte, dependendo de qual
    transporte estivesse ativo.
    """
    assert _violacoes("boundary/http", "boundary.gateway") == []
    assert _violacoes("boundary/amqp", "boundary.gateway") == []


def test_o_worker_nao_tem_camada_de_persistencia():
    """Regra 1 da arquitetura: o Java e o dono do banco.

    Este teste falha no dia em que alguem adicionar um ORM ou um driver de
    banco ao worker — que e exatamente quando a regra estaria sendo quebrada,
    e o momento em que ninguem se lembraria dela.
    """
    proibidos = {"sqlalchemy", "psycopg", "psycopg2", "asyncpg", "sqlite3", "pymongo"}

    for arquivo in _modulos(""):
        arvore = ast.parse(arquivo.read_text(encoding="utf-8"), filename=str(arquivo))
        for no in ast.walk(arvore):
            nomes = []
            if isinstance(no, ast.Import):
                nomes = [a.name.split(".")[0] for a in no.names]
            elif isinstance(no, ast.ImportFrom) and no.module:
                nomes = [no.module.split(".")[0]]

            assert not (set(nomes) & proibidos), (
                f"{arquivo.relative_to(APP)} importa acesso a banco: "
                "o worker e stateless por desenho"
            )
