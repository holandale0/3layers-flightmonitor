from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_retorna_up():
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "UP"
    assert body["service"] == "flight-monitor-worker"


def test_health_reporta_prontidao_dos_providers():
    """O health precisa dizer QUAL camada caiu, e nao apenas que algo caiu.

    A camada 2 e desligavel por configuracao (FASTFLIGHTS_ENABLED). Quando ela
    for desligada, isso tem que aparecer aqui — degradacao silenciosa e o que
    queremos evitar.
    """
    body = client.get("/health").json()
    assert set(body["providers"]) == {"travelpayouts", "fast_flights"}
    assert isinstance(body["providers"]["fast_flights"], bool)


def test_rota_inexistente_retorna_404():
    assert client.get("/nao-existe").status_code == 404


def test_openapi_disponivel():
    resp = client.get("/openapi.json")
    assert resp.status_code == 200
    assert "/health" in resp.json()["paths"]
