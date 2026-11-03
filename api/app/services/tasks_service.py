from __future__ import annotations

from collections import Counter

import sqlite3

from app.repositories.tasks_repository import TasksRepository
from app.schemas import SummaryResponse, TaskCreateRequest, TaskResponse
from app.security import Identity

CLOSED_STATUSES = frozenset({"DONE", "ARCHIVED"})
HIGH_PRIORITY = "HIGH"


class TasksService:
    def __init__(self, repository: TasksRepository) -> None:
        self.repository = repository

    def list_tasks(self, identity: Identity) -> list[TaskResponse]:
        rows = self.repository.list_by_owner(identity["username"])
        return [self._to_response(row) for row in rows]

    def create_task(self, payload: TaskCreateRequest, identity: Identity) -> TaskResponse:
        row = self.repository.create(
            title=payload.title,
            description=payload.description,
            status=payload.status,
            owner=identity["username"],
            priority=payload.priority,
        )
        return self._to_response(row)

    def summary(self, identity: Identity) -> SummaryResponse:
        owner = identity["username"]
        rows = self.repository.summary_rows(owner)
        status_counts = Counter(row["status"] for row in rows)
        high_priority_open = sum(
            row["priority"] == HIGH_PRIORITY and row["status"] not in CLOSED_STATUSES
            for row in rows
        )
        return SummaryResponse(
            owner=owner,
            total=len(rows),
            by_status=dict(status_counts),
            high_priority_open=high_priority_open,
        )

    @staticmethod
    def _to_response(row: sqlite3.Row) -> TaskResponse:
        return TaskResponse(
            id=row["id"],
            title=row["title"],
            description=row["description"],
            status=row["status"],
            owner=row["owner"],
            priority=row["priority"],
            created_at=row["created_at"],
        )