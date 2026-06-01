# Mock-only image for the public BullTap demo backend (MOCK_MODE=yes).
#
# Installs just the FastAPI stack — NOT ib-async / longport — so the image stays
# tiny and needs no IBKR/Longbridge credentials or IB Gateway. The mock router
# (app/routes/mock.py) serves seeded fake data for every endpoint, incl. the
# /ws/quotes WebSocket. Do NOT use this image for the real (non-mock) backend.
FROM python:3.12-slim

WORKDIR /app

# Slim runtime deps only — matches what app.main needs in mock mode.
RUN pip install --no-cache-dir \
    "fastapi>=0.115.0" \
    "uvicorn[standard]>=0.32.0" \
    "pydantic>=2.9.0" \
    "pydantic-settings>=2.5.0" \
    "httpx>=0.27.0"

COPY app ./app

# Public-demo config baked in (neither is sensitive: the token only unlocks mock
# data). MOCK_MODE keeps it credential-free; API_TOKEN matches the app default.
ENV MOCK_MODE=yes
ENV API_TOKEN=bulltap-demo-public
EXPOSE 8000

# Shell form so Railway's injected $PORT is honored; falls back to 8000 elsewhere.
CMD uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}
