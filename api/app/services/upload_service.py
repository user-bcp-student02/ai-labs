from __future__ import annotations

import os
from pathlib import Path

from fastapi import UploadFile

from app.schemas import UploadResponse

DEFAULT_MAX_UPLOAD_BYTES = 1_048_576
DEFAULT_ALLOWED_EXTENSIONS = frozenset({".txt", ".md", ".pdf"})
MAX_UPLOAD_BYTES_ENV = "MAX_UPLOAD_BYTES"
ALLOWED_EXTENSIONS_ENV = "UPLOAD_ALLOWED_EXTENSIONS"
UNSUPPORTED_FILE_TYPE = "Unsupported file type"
FILE_TOO_LARGE = "File too large"
INVALID_FILE_NAME = "Invalid file name"


class UploadValidationError(Exception):
    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


class UploadService:
    def __init__(
        self,
        upload_dir: str,
        max_upload_bytes: int,
        allowed_extensions: frozenset[str],
    ) -> None:
        self.upload_dir = Path(upload_dir)
        self.max_upload_bytes = max_upload_bytes
        self.allowed_extensions = allowed_extensions

    @classmethod
    def from_environment(cls, upload_dir: str) -> UploadService:
        max_upload_bytes = int(
            os.getenv(MAX_UPLOAD_BYTES_ENV, str(DEFAULT_MAX_UPLOAD_BYTES))
        )
        if max_upload_bytes <= 0:
            raise ValueError(f"{MAX_UPLOAD_BYTES_ENV} must be positive")
        configured_extensions = os.getenv(ALLOWED_EXTENSIONS_ENV)
        extensions = (
            DEFAULT_ALLOWED_EXTENSIONS
            if configured_extensions is None
            else frozenset(
                extension.strip().lower()
                if extension.strip().startswith(".")
                else f".{extension.strip().lower()}"
                for extension in configured_extensions.split(",")
                if extension.strip()
            )
        )
        return cls(upload_dir, max_upload_bytes, extensions)

    async def store(self, upload: UploadFile, owner: str) -> UploadResponse:
        filename = self._normalize_filename(upload.filename or "")
        if Path(filename).suffix.lower() not in self.allowed_extensions:
            raise UploadValidationError(400, UNSUPPORTED_FILE_TYPE)

        content = await upload.read(self.max_upload_bytes + 1)
        if len(content) > self.max_upload_bytes:
            raise UploadValidationError(413, FILE_TOO_LARGE)

        target = self.upload_dir / filename
        target.write_bytes(content)
        return UploadResponse(
            owner=owner,
            file_name=filename,
            stored_at=str(target),
            size=len(content),
        )

    @staticmethod
    def _normalize_filename(filename: str) -> str:
        normalized = Path(filename.replace("\\", "/")).name
        if not normalized or normalized in {".", ".."} or "\x00" in normalized:
            raise UploadValidationError(400, INVALID_FILE_NAME)
        return normalized