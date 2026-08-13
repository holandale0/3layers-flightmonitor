"""Configuração do worker.

As credenciais vêm do `.env` na raiz do projeto — o mesmo arquivo usado pelo
docker-compose e pelo core-java. Fonte única, sem duplicação de segredo.
"""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT_ENV = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=ROOT_ENV,
        env_file_encoding="utf-8",
        extra="ignore",  # o .env também tem chaves do Postgres e do WhatsApp
    )

    service_name: str = "flight-monitor-worker"
    version: str = "0.1.0"
    worker_port: int = 8001
    log_level: str = "INFO"

    # Camada 1 da coleta — preenchido na etapa E1.5
    travelpayouts_token: str = ""

    # Camada 2 da coleta. Chave de desligamento: o fast-flights depende do
    # formato interno do Google e vai quebrar em algum momento. Quando quebrar,
    # basta FASTFLIGHTS_ENABLED=false no .env — o sistema degrada e segue
    # funcionando, sem precisar de deploy.
    fastflights_enabled: bool = True

    # --- Mensageria, etapa E4.1 ---
    # Segunda porta de entrada do worker, ao lado do HTTP. Desligada por padrao:
    # quem manda no transporte e o core, e ele so publica quando configurado
    # para AMQP.
    amqp_enabled: bool = False

    # A MESMA variavel que o core usa para escolher o transporte.
    #
    # Existe porque a E4.2 tropecou nisto: o compose passava WORKER_TRANSPORTE
    # para os dois servicos, e o worker — que so conhecia AMQP_ENABLED — subia
    # sem consumidor. As filas apareciam declaradas no RabbitMQ com
    # `consumers = 0`, e uma busca por fila ficaria esperando para sempre.
    #
    # Duas variaveis para uma decisao so e convite para ficarem em desacordo, e
    # o desacordo aqui e silencioso. Agora qualquer uma das duas liga o
    # consumidor: quem ja usa AMQP_ENABLED continua valendo.
    worker_transporte: str = "REST"

    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "flightmon"
    rabbitmq_password: str = "flightmon"

    # --- Linguagem natural, etapa E3.1 ---
    # Sem chave, a interpretacao acontece so por regras. Nao e degradacao
    # silenciosa: o campo `provider` da resposta diz quem interpretou.
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-sonnet-5"

    # Troca as duas camadas por fontes falsas e deterministas (etapa E1.16).
    # NAO tem valor no .env de proposito: ligado apenas pelo script de E2E, via
    # variavel de ambiente do processo. Um `true` esquecido no .env deixaria o
    # sistema inventando precos em silencio.
    use_fake_providers: bool = False

    @property
    def travelpayouts_configured(self) -> bool:
        return bool(self.travelpayouts_token.strip())

    @property
    def anthropic_configured(self) -> bool:
        return bool(self.anthropic_api_key.strip())

    @property
    def deve_consumir_da_fila(self) -> bool:
        """O consumidor AMQP deve subir?

        Sim se QUALQUER uma das duas chaves pedir. Ver `worker_transporte`.
        """
        return self.amqp_enabled or self.worker_transporte.strip().upper() == "AMQP"

    @property
    def amqp_url(self) -> str:
        return (
            f"amqp://{self.rabbitmq_user}:{self.rabbitmq_password}"
            f"@{self.rabbitmq_host}:{self.rabbitmq_port}/"
        )


@lru_cache
def get_settings() -> Settings:
    """Instância única, cacheada — evita reler o .env a cada requisição."""
    return Settings()
