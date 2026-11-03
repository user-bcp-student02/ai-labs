from __future__ import annotations

import os
import sqlite3
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

import urllib3
from fastapi import Depends, FastAPI, File, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.repositories.tasks_repository import TasksRepository
from app.schemas import BackendHealthResponse, SummaryResponse, TaskCreateRequest, TaskResponse, UploadResponse
from app.security import Identity, get_identity
from app.services.tasks_service import TasksService
from app.services.upload_service import UploadService, UploadValidationError

DEFAULT_AUTH_HEALTH_URL = "http://localhost:8080/q/health"
BACKEND_HEALTH_TIMEOUT_SECONDS = 3.0
BACKEND_UNAVAILABLE_DETAIL = "Backend unavailable"


def create_app(db_path: str | None = None, upload_dir: str | None = None) -> FastAPI:
    selected_db = db_path or os.getenv("WORKSHOP_DB_PATH", "./data/workshop.db")
    selected_uploads = upload_dir or os.getenv("WORKSHOP_UPLOAD_DIR", "./uploads")
    Path(selected_db).parent.mkdir(parents=True, exist_ok=True)
    Path(selected_uploads).mkdir(parents=True, exist_ok=True)

    connection = sqlite3.connect(selected_db, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    repository = TasksRepository(connection)
    repository.initialize()
    tasks_service = TasksService(repository)
    upload_service = UploadService.from_environment(selected_uploads)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            connection.close()

    app = FastAPI(title="Workshop Requirements API", version="0.1.0", lifespan=lifespan)
    app.state.upload_dir = selected_uploads
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/api/tasks", response_model=list[TaskResponse])
    def list_tasks(
        owner: str | None = Query(default=None),
        identity: Identity = Depends(get_identity),
    ) -> list[TaskResponse]:
        return tasks_service.list_tasks(identity)

    @app.post("/api/tasks", status_code=201, response_model=TaskResponse)
    def create_task(
        payload: TaskCreateRequest,
        identity: Identity = Depends(get_identity),
    ) -> TaskResponse:
        return tasks_service.create_task(payload, identity)

    @app.get("/api/summary", response_model=SummaryResponse)
    def summary(
        owner: str | None = Query(default=None),
        identity: Identity = Depends(get_identity),
    ) -> SummaryResponse:
        return tasks_service.summary(identity)

    @app.post("/api/requirements", status_code=201, response_model=UploadResponse)
    async def upload_requirement(
        file: UploadFile = File(...),
        identity: Identity = Depends(get_identity),
    ) -> UploadResponse:
        try:
            return await upload_service.store(file, identity["username"])
        except UploadValidationError as error:
            raise HTTPException(status_code=error.status_code, detail=error.detail) from error

    @app.get("/api/backend-health", response_model=BackendHealthResponse)
    def backend_health(identity: Identity = Depends(get_identity)) -> BackendHealthResponse:
        url = os.getenv("AUTH_HEALTH_URL", DEFAULT_AUTH_HEALTH_URL)
        try:
            response = urllib3.PoolManager().request(
                "GET", url, timeout=BACKEND_HEALTH_TIMEOUT_SECONDS
            )
        except urllib3.exceptions.HTTPError:
            return BackendHealthResponse(
                requested_by=identity["username"],
                status=0,
                body=BACKEND_UNAVAILABLE_DETAIL,
            )
        return BackendHealthResponse(
            requested_by=identity["username"],
            status=response.status,
            body=response.data.decode("utf-8"),
        )

    return app


app = create_app()
