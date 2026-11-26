from fastapi.testclient import TestClient

from app.main import create_app


def test_health_and_prometheus(monkeypatch):
    monkeypatch.setenv("EUREKA_ENABLED", "false")
    from app.config import get_settings
    get_settings.cache_clear()
    with TestClient(create_app()) as c:
        assert c.get("/actuator/health").json() == {"status": "UP"}
        r = c.get("/actuator/prometheus")
        assert r.status_code == 200 and "python_info" in r.text
