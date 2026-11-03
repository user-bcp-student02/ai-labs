from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

DEFAULT_TASK_STATUS = "TODO"
DEFAULT_TASK_PRIORITY = "MEDIUM"


class TaskCreateRequest(BaseModel):
    title: str = ""
    description: str = ""
    status: str = DEFAULT_TASK_STATUS
    priority: str = DEFAULT_TASK_PRIORITY
    owner: str | None = None


class TaskResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str
    description: str
    status: str
    owner: str
    priority: str
    created_at: str = Field(alias="createdAt")


class SummaryResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    owner: str
    total: int
    by_status: dict[str, int] = Field(alias="byStatus")
    high_priority_open: int = Field(alias="highPriorityOpen")


class UploadResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    owner: str
    file_name: str = Field(alias="fileName")
    stored_at: str = Field(alias="storedAt")
    size: int


class BackendHealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    requested_by: str = Field(alias="requestedBy")
    status: int
    body: str