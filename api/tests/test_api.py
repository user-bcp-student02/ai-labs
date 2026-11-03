import asyncio
import time
from pathlib import Path

import base64
import httpx
import jwt
import pytest
import urllib3
from fastapi.testclient import TestClient

import app.main as api_module
from app.main import create_app

JWT_SECRET = "api-test-secret-not-for-production"
JWT_ISSUER = "ai-labs-api-tests"
JWT_AUDIENCE = "ai-labs-api-tests"


@pytest.fixture(autouse=True)
def configure_test_jwt(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("JWT_SECRET", JWT_SECRET)
    monkeypatch.setenv("JWT_ISSUER", JWT_ISSUER)
    monkeypatch.setenv("JWT_AUDIENCE", JWT_AUDIENCE)


def token(username: str = "student", role: str = "STUDENT") -> str:
    return jwt.encode(
        {
            "sub": username,
            "role": role,
            "exp": int(time.time()) + 3600,
            "iss": JWT_ISSUER,
            "aud": JWT_AUDIENCE,
        },
        JWT_SECRET,
        algorithm="HS256",
    )


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()
    assert response.json() == {
        "owner": "student",
        "fileName": "requirements.txt",
        "storedAt": str(upload_dir / "requirements.txt"),
        "size": len(b"Login must be accessible"),
    }


def test_upload_rejects_a_filename_collision_without_overwriting(
    tmp_path: Path,
) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        first_response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"first", "text/plain")},
        )
        second_response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"second", "text/plain")},
        )

    assert first_response.status_code == 201
    assert second_response.status_code == 409
    assert (upload_dir / "requirements.txt").read_bytes() == b"first"
    assert list(upload_dir.iterdir()) == [upload_dir / "requirements.txt"]


def test_concurrent_uploads_cannot_replace_the_same_filename(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))

    async def send_uploads() -> list[httpx.Response]:
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(
                transport=httpx.ASGITransport(app=app),
                base_url="http://testserver",
            ) as client:
                return await asyncio.gather(
                    client.post(
                        "/api/requirements",
                        headers=headers(),
                        files={"file": ("shared.txt", b"first", "text/plain")},
                    ),
                    client.post(
                        "/api/requirements",
                        headers=headers(),
                        files={"file": ("shared.txt", b"second", "text/plain")},
                    ),
                )

    responses = asyncio.run(send_uploads())

    assert sorted(response.status_code for response in responses) == [201, 409]
    assert (upload_dir / "shared.txt").read_bytes() in {b"first", b"second"}


def test_upload_openapi_preserves_multipart_file_contract(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))

    with TestClient(app):
        operation = app.openapi()["paths"]["/api/requirements"]["post"]
    request_schema = operation["requestBody"]["content"]["multipart/form-data"]["schema"]

    assert request_schema["properties"]["file"] == {
        "type": "string",
        "format": "binary",
    }
    assert request_schema["required"] == ["file"]
    assert {"400", "409", "413"}.issubset(operation["responses"])


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        invalid_headers = {"Authorization": "Bearer invalid"}
        responses = [
            client.get("/api/tasks", headers=invalid_headers),
            client.get("/api/summary", headers=invalid_headers),
            client.post("/api/requirements", headers=invalid_headers, files={"file": ("a.txt", b"a")}),
            client.get("/api/backend-health", headers=invalid_headers),
        ]
    assert [response.status_code for response in responses] == [401, 401, 401, 401]


def test_preserves_endpoint_response_contracts(
    tmp_path: Path, monkeypatch
) -> None:
    class FakeResponse:
        status = 204
        data = b"healthy"

    class FakePoolManager:
        def request(self, method: str, url: str, timeout: float) -> FakeResponse:
            return FakeResponse()

    monkeypatch.setattr(api_module.urllib3, "PoolManager", FakePoolManager)
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    auth_headers = headers()

    with TestClient(app) as client:
        listed = client.get("/api/tasks", headers=auth_headers)
        created = client.post(
            "/api/tasks",
            headers=auth_headers,
            json={"title": "Characterization", "description": "baseline"},
        )
        summary = client.get("/api/summary", headers=auth_headers)
        uploaded = client.post(
            "/api/requirements",
            headers=auth_headers,
            files={"file": ("spec.txt", b"requirements", "text/plain")},
        )
        backend = client.get("/api/backend-health", headers=auth_headers)

    assert listed.status_code == 200
    assert set(listed.json()[0]) == {
        "id", "title", "description", "status", "owner", "priority", "createdAt"
    }
    assert created.status_code == 201
    assert set(created.json()) == set(listed.json()[0])
    assert summary.json() == {
        "owner": "student",
        "total": 3,
        "byStatus": {"TODO": 2, "IN_PROGRESS": 1},
        "highPriorityOpen": 1,
    }
    assert uploaded.status_code == 201
    assert set(uploaded.json()) == {"owner", "fileName", "storedAt", "size"}
    assert backend.json() == {"requestedBy": "student", "status": 204, "body": "healthy"}


def test_rejects_legacy_unsigned_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    legacy = base64.b64encode(b"student|STUDENT|4102444800|WORKSHOP_SECRET_2026").decode()
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": f"Bearer {legacy}"})
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


@pytest.mark.parametrize(
    "invalid_claim",
    ["expired", "issuer", "audience", "missing_subject", "missing_expiration"],
)
def test_rejects_jwt_with_invalid_claims(tmp_path: Path, invalid_claim: str) -> None:
    claims: dict[str, object] = {
        "sub": "student",
        "role": "STUDENT",
        "exp": int(time.time()) + 3600,
        "iss": JWT_ISSUER,
        "aud": JWT_AUDIENCE,
    }
    if invalid_claim == "expired":
        claims["exp"] = int(time.time()) - 1
    elif invalid_claim == "issuer":
        claims["iss"] = "untrusted-issuer"
    elif invalid_claim == "audience":
        claims["aud"] = "untrusted-audience"
    elif invalid_claim == "missing_expiration":
        claims.pop("exp")
    else:
        claims.pop("sub")
    signed_token = jwt.encode(claims, JWT_SECRET, algorithm="HS256")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers={"Authorization": f"Bearer {signed_token}"},
        )
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


def test_rejects_requests_when_jwt_configuration_is_missing(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.delenv("JWT_SECRET")
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


def test_owner_is_bound_to_authenticated_identity(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        listed = client.get("/api/tasks?owner=trainer", headers=headers())
        summary = client.get("/api/summary?owner=trainer", headers=headers())
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Assigned by client", "owner": "trainer"},
        )
    assert {task["owner"] for task in listed.json()} == {"student"}
    assert summary.json()["owner"] == "student"
    assert summary.json()["total"] == 2
    assert created.json()["owner"] == "student"


def test_task_title_with_apostrophe_is_stored_as_plain_text(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Owner's notes", "description": "Plain text"},
        )
    assert response.status_code == 201
    assert response.json()["title"] == "Owner's notes"


def test_upload_normalizes_filename_before_writing(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("../escaped.txt", b"safe", "text/plain")},
        )
    assert response.status_code == 201
    assert response.json()["fileName"] == "escaped.txt"
    assert (upload_dir / "escaped.txt").read_bytes() == b"safe"
    assert not (tmp_path / "escaped.txt").exists()


def test_upload_rejects_disallowed_extension(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("payload.exe", b"content", "application/octet-stream")},
        )
    assert response.status_code == 400
    assert response.json() == {"detail": "Unsupported file type"}


def test_upload_rejects_files_over_configured_limit(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    upload_dir = tmp_path / "uploads"
    monkeypatch.setenv("MAX_UPLOAD_BYTES", "4")
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("large.txt", b"12345", "text/plain")},
        )
    assert response.status_code == 413
    assert response.json() == {"detail": "File too large"}
    assert list(upload_dir.iterdir()) == []


def test_upload_accepts_file_at_configured_limit(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    upload_dir = tmp_path / "uploads"
    monkeypatch.setenv("MAX_UPLOAD_BYTES", "4")
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("exact.txt", b"1234", "text/plain")},
        )

    assert response.status_code == 201
    assert response.json()["size"] == 4
    assert (upload_dir / "exact.txt").read_bytes() == b"1234"


def test_upload_without_file_keeps_required_field_validation(
    tmp_path: Path,
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            data={"description": "no file"},
        )

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", "file"]


def test_upload_rejects_oversize_before_consuming_the_remaining_body(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    boundary = "upload-boundary"
    body_chunks = [
        (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="large.txt"\r\n'
            "Content-Type: text/plain\r\n\r\n"
        ).encode(),
        b"1234",
        b"5",
        f"\r\n--{boundary}--\r\n".encode(),
    ]
    consumed_chunks: list[int] = []

    async def request_body():
        for index, chunk in enumerate(body_chunks):
            consumed_chunks.append(index)
            yield chunk

    upload_dir = tmp_path / "uploads"
    monkeypatch.setenv("MAX_UPLOAD_BYTES", "4")
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))

    async def send_streaming_request() -> httpx.Response:
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(
                transport=httpx.ASGITransport(app=app),
                base_url="http://testserver",
            ) as client:
                return await client.post(
                    "/api/requirements",
                    headers={
                        **headers(),
                        "Content-Type": f"multipart/form-data; boundary={boundary}",
                    },
                    content=request_body(),
                )

    response = asyncio.run(send_streaming_request())

    assert response.status_code == 413
    assert consumed_chunks == [0, 1, 2]
    assert list(upload_dir.iterdir()) == []


def test_backend_health_does_not_expose_transport_error(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    class FailedPoolManager:
        def request(self, method: str, url: str, timeout: float) -> None:
            raise urllib3.exceptions.NewConnectionError(None, "internal-proxy-secret")

    monkeypatch.setattr(api_module.urllib3, "PoolManager", FailedPoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.json() == {
        "requestedBy": "student",
        "status": 0,
        "body": "Backend unavailable",
    }
