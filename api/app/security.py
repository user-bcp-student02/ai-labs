from __future__ import annotations

import os
from typing import TypedDict

import jwt
from fastapi import Header, HTTPException

ALGORITHM = "HS256"
INVALID_TOKEN_DETAIL = "Invalid token"


class Identity(TypedDict):
    username: str
    role: str


def get_identity(authorization: str = Header(default="")) -> Identity:
    secret = os.getenv("JWT_SECRET", "")
    issuer = os.getenv("JWT_ISSUER", "")
    audience = os.getenv("JWT_AUDIENCE", "")
    scheme, separator, token = authorization.partition(" ")
    if not secret or not issuer or not audience or scheme.lower() != "bearer" or not separator or not token:
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL)

    try:
        claims = jwt.decode(
            token,
            secret,
            algorithms=[ALGORITHM],
            issuer=issuer,
            audience=audience,
            options={"require": ["exp", "iss", "aud", "sub"]},
        )
    except jwt.PyJWTError as error:
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL) from error

    username = claims.get("sub")
    role = claims.get("role")
    if not isinstance(username, str) or not username or not isinstance(role, str) or not role:
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL)
    return {"username": username, "role": role}