from fastapi.testclient import TestClient

from app import main
from app.config import Settings, get_settings
from app.main import get_queue


class DummyJob:
    def __init__(self, job_id: str = "job-123", status: str = "queued"):
        self.id = job_id
        self._status = status
        self.result = None
        self.exc_info = None

    def get_status(self, refresh: bool = False):
        return self._status


class DummyQueue:
    def __init__(self):
        self.connection = None

    def enqueue(self, fn, payload):
        return DummyJob()


def override_settings():
    return Settings(
        openai_api_key="test-key",
        allow_anonymous=True,
        redis_url="redis://localhost:6379/0",
    )


def override_queue():
    return DummyQueue()


main.app.dependency_overrides[get_settings] = override_settings
main.app.dependency_overrides[get_queue] = override_queue

client = TestClient(main.app)


def test_status_ok():
    resp = client.get("/api/status")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_enqueue_assessment():
    resp = client.post("/api/assessments", json={"github_username": "octocat"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["job_id"]
    assert data["status"] in {"queued", "deferred", "started", "finished"}


def test_get_assessment_not_found(monkeypatch):
    def fake_fetch(job_id, connection=None):
        raise Exception("missing")

    monkeypatch.setattr("app.main.Job.fetch", fake_fetch)
    resp = client.get("/api/assessments/unknown")
    assert resp.status_code == 404
