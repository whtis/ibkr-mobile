from fastapi import Header, HTTPException, status

from .config import settings


def require_token(authorization: str | None = Header(None)) -> None:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing bearer token")
    if authorization.removeprefix("Bearer ") != settings.api_token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid token")
