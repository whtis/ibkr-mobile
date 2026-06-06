from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    api_token: str = "mock-token-change-me"
    ib_host: str = "127.0.0.1"
    ib_port: int = 4002
    ib_client_id: int = 10

    # Longbridge OpenAPI (for quotes + K-lines). Leave empty to fall back to IBKR.
    longport_app_key: str = ""
    longport_app_secret: str = ""
    longport_access_token: str = ""

    # When true, the backend serves seeded fake data instead of talking to IBKR or
    # Longbridge. Lets contributors run the whole stack without any real account.
    mock_mode: bool = False

    # --- Order safety guards (see app/routes/orders.py) ---
    enable_order_guards: bool = True
    max_order_qty: float = 1000.0
    max_order_notional_usd: float = 100_000.0
    order_rate_limit_per_min: int = 10


settings = Settings()
