from __future__ import annotations

import os
from collections.abc import AsyncGenerator, Callable
from pathlib import Path

from starlette.datastructures import FormData, Headers, UploadFile
from starlette.formparsers import MultiPartException, MultiPartParser
from starlette.requests import Request

from app.schemas import UploadResponse

DEFAULT_MAX_UPLOAD_BYTES = 1_048_576
DEFAULT_ALLOWED_EXTENSIONS = frozenset({".txt", ".md", ".pdf"})
MAX_UPLOAD_BYTES_ENV = "MAX_UPLOAD_BYTES"
ALLOWED_EXTENSIONS_ENV = "UPLOAD_ALLOWED_EXTENSIONS"
UNSUPPORTED_FILE_TYPE = "Unsupported file type"
FILE_TOO_LARGE = "File too large"
INVALID_FILE_NAME = "Invalid file name"
FILE_ALREADY_EXISTS = "File already exists"
MULTIPART_PARSE_ERROR = "There was an error parsing the body"


class _UploadRejected(MultiPartException):
    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


class _UploadSizeLimitedParser(MultiPartParser):
    def __init__(
        self,
        headers: Headers,
        stream: AsyncGenerator[bytes, None],
        max_file_bytes: int,
        validate_filename: Callable[[str], str],
    ) -> None:
        super().__init__(headers, stream)
        self.max_file_bytes = max_file_bytes
        self.validate_filename = validate_filename
        self.current_file_bytes = 0

    def on_part_begin(self) -> None:
        super().on_part_begin()
        self.current_file_bytes = 0

    def on_headers_finished(self) -> None:
        super().on_headers_finished()
        if self._current_part.file is None:
            return
        try:
            self.validate_filename(self._current_part.file.filename or "")
        except UploadValidationError as error:
            raise _UploadRejected(error.status_code, error.detail) from error

    def on_part_data(self, data: bytes, start: int, end: int) -> None:
        part_bytes = end - start
        if self._current_part.file is not None:
            next_file_bytes = self.current_file_bytes + part_bytes
            if next_file_bytes > self.max_file_bytes:
                raise _UploadRejected(413, FILE_TOO_LARGE)
            self.current_file_bytes = next_file_bytes
        super().on_part_data(data, start, end)


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

    async def parse_request(self, request: Request) -> FormData:
        content_type = request.headers.get("content-type", "").partition(";")[0]
        if content_type.strip().lower() != "multipart/form-data":
            return FormData()

        parser = _UploadSizeLimitedParser(
            request.headers,
            request.stream(),
            self.max_upload_bytes,
            self._validate_filename,
        )
        try:
            return await parser.parse()
        except _UploadRejected as error:
            raise UploadValidationError(error.status_code, error.detail) from error
        except MultiPartException as error:
            raise UploadValidationError(400, MULTIPART_PARSE_ERROR) from error

    async def store(self, upload: UploadFile, owner: str) -> UploadResponse:
        filename = self._validate_filename(upload.filename or "")

        content = await upload.read(self.max_upload_bytes + 1)
        if len(content) > self.max_upload_bytes:
            raise UploadValidationError(413, FILE_TOO_LARGE)

        target = self.upload_dir / filename
        created = False
        try:
            with target.open("xb") as destination:
                created = True
                destination.write(content)
        except FileExistsError as error:
            raise UploadValidationError(409, FILE_ALREADY_EXISTS) from error
        except OSError:
            if created:
                target.unlink(missing_ok=True)
            raise
        return UploadResponse(
            owner=owner,
            file_name=filename,
            stored_at=str(target),
            size=len(content),
        )

    def _validate_filename(self, filename: str) -> str:
        normalized = self._normalize_filename(filename)
        if Path(normalized).suffix.lower() not in self.allowed_extensions:
            raise UploadValidationError(400, UNSUPPORTED_FILE_TYPE)
        return normalized

    @staticmethod
    def _normalize_filename(filename: str) -> str:
        normalized = Path(filename.replace("\\", "/")).name
        if not normalized or normalized in {".", ".."} or "\x00" in normalized:
            raise UploadValidationError(400, INVALID_FILE_NAME)
        return normalized