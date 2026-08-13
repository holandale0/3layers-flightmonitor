import pytest


@pytest.fixture
def anyio_backend() -> str:
    """Roda os testes assincronos apenas em asyncio, e nao tambem em trio."""
    return "asyncio"
