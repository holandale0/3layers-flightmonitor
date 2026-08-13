"""E2E entre servicos — etapa E1.16.

Sobe o worker Python com fontes falsas, roda o `E2EServicosTest` do core-java
contra ele e derruba tudo no final.

    python scripts/e2e_servicos.py                     # transporte REST
    python scripts/e2e_servicos.py --transporte amqp   # transporte AMQP (E4.1)

# Por que um script, e nao `mvn test`

O teste depende de outro processo no ar. Deixa-lo no build padrao faria a suite
inteira falhar na maquina de quem nao subiu o worker — e a licao aqui e a mesma
do canario da E4.5: teste que falha por motivo ambiental treina a equipe a
ignorar falha.

Sem `-De2e.servicos=true` a classe e simplesmente pulada, entao `mvn test`
continua verde e rapido.

# O que e real e o que e falso

    real   Java, Python, PostgreSQL, HTTP entre eles, transacoes, Flyway
    falso  APENAS as fontes externas (Travelpayouts e Google), via
           USE_FAKE_PROVIDERS=true

# Porta separada de proposito

O worker falso sobe na 8002, nao na 8001. Se ele usasse a porta do worker de
desenvolvimento, ou o script derrubaria o worker que voce esta usando, ou —
pior — o teste falaria com o worker REAL e afirmaria coisas sobre precos de
verdade. O `estaFalandoComOWorkerFalso` existe como ultima linha de defesa
contra exatamente isso.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
WORKER = RAIZ / "worker-python"
CORE = RAIZ / "core-java"

PORTA_WORKER = int(os.environ.get("E2E_WORKER_PORT", "8002"))
URL_WORKER = f"http://localhost:{PORTA_WORKER}"

#: REST (padrao) ou AMQP. Com AMQP o worker ganha a segunda porta de entrada, e
#: o core publica em vez de chamar HTTP — mas os testes sao os mesmos.
TRANSPORTE = "AMQP" if "--transporte" in sys.argv and "amqp" in sys.argv else "REST"

SEGUNDOS_ATE_DESISTIR = 40


def log(msg: str) -> None:
    print(f"[e2e] {msg}", flush=True)


def python_do_worker() -> str:
    """O interpretador do venv do worker, com queda para o do sistema."""
    venv = WORKER / ".venv" / ("Scripts" if os.name == "nt" else "bin") / "python.exe"
    if venv.exists():
        return str(venv)
    venv_posix = WORKER / ".venv" / "bin" / "python"
    if venv_posix.exists():
        return str(venv_posix)
    log("AVISO: venv do worker nao encontrado; usando o Python do sistema")
    return sys.executable


def porta_livre() -> bool:
    try:
        urllib.request.urlopen(f"{URL_WORKER}/health", timeout=2)
    except urllib.error.URLError:
        return True
    except OSError:
        return True
    return False


def subir_worker() -> subprocess.Popen:
    ambiente = os.environ.copy()
    # A chave que troca as duas camadas por fontes falsas. Fica so aqui: no
    # .env ela seria um pe-de-cabra esquecido, deixando o sistema inventar
    # precos em producao.
    ambiente["USE_FAKE_PROVIDERS"] = "true"
    ambiente["LOG_LEVEL"] = "INFO" if TRANSPORTE == "AMQP" else "WARNING"

    if TRANSPORTE == "AMQP":
        # O consumidor espera a topologia aparecer: quem declara as filas e o
        # core, que so sobe quando o maven rodar. Ver ConsumidorDaBusca.
        ambiente["AMQP_ENABLED"] = "true"

    log(f"subindo worker FALSO na porta {PORTA_WORKER}, transporte {TRANSPORTE}")
    return subprocess.Popen(
        [
            python_do_worker(),
            "-m",
            "uvicorn",
            "app.main:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(PORTA_WORKER),
            "--log-level",
            "warning",
        ],
        cwd=WORKER,
        env=ambiente,
    )


def esperar_worker() -> None:
    limite = time.monotonic() + SEGUNDOS_ATE_DESISTIR
    while time.monotonic() < limite:
        try:
            with urllib.request.urlopen(f"{URL_WORKER}/health", timeout=2) as r:
                if r.status == 200:
                    log("worker falso no ar")
                    return
        except (urllib.error.URLError, OSError):
            time.sleep(0.5)
    raise TimeoutError(f"o worker falso nao subiu em {SEGUNDOS_ATE_DESISTIR}s")


def rodar_teste() -> int:
    mvn = shutil.which("mvn") or shutil.which("mvn.cmd")
    if mvn is None:
        raise RuntimeError("mvn nao encontrado no PATH")

    log(f"rodando E2EServicosTest contra o worker falso, por {TRANSPORTE}")
    return subprocess.call(
        [
            mvn,
            "-B",
            "test",
            "-Dtest=E2EServicosTest",
            "-DfailIfNoSpecifiedTests=false",
            "-De2e.servicos=true",
            f"-De2e.worker.url={URL_WORKER}",
            f"-De2e.transporte={TRANSPORTE}",
            # As propriedades precisam chegar ao JVM do teste, nao so ao do
            # Maven. Sem isto o @EnabledIfSystemProperty nao ve nada e a classe
            # e pulada — o build passaria sem ter testado coisa alguma.
            f"-DargLine=-De2e.servicos=true -De2e.worker.url={URL_WORKER} "
            f"-De2e.transporte={TRANSPORTE}",
        ],
        cwd=CORE,
    )


def derrubar(processo: subprocess.Popen) -> None:
    if processo.poll() is not None:
        return
    log("derrubando worker falso")
    processo.terminate()
    try:
        processo.wait(timeout=10)
    except subprocess.TimeoutExpired:
        processo.kill()


def main() -> int:
    if not porta_livre():
        log(f"ERRO: ja ha algo escutando em {URL_WORKER}.")
        log("Se for o worker de desenvolvimento, use outra porta:")
        log("  E2E_WORKER_PORT=8003 python scripts/e2e_servicos.py")
        return 2

    worker = subir_worker()
    try:
        esperar_worker()
        codigo = rodar_teste()
    finally:
        derrubar(worker)

    if codigo == 0:
        log(f"OK — Java e Python fecharam o contrato por {TRANSPORTE}, sem stub entre eles")
    else:
        log(f"FALHOU — maven saiu com codigo {codigo}")
    return codigo


if __name__ == "__main__":
    raise SystemExit(main())
