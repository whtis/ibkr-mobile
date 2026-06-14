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

    # --- Push notifications (FCM) ---
    # Preferred path: a Cloudflare Worker relay that reaches Google for us (the
    # NAS is behind the GFW). When fcm_relay_url is set, notify uses it.
    fcm_relay_url: str = ""
    fcm_relay_secret: str = ""
    # Fallback: direct firebase-admin send (only works where Google is reachable).
    # Path to the Firebase service-account JSON. Empty disables the direct path.
    fcm_service_account_path: str = ""
    # IB Gateway container name watched by the 2FA monitor (app/gateway_monitor.py).
    gateway_container: str = "ibkr-gateway"
    # Minimum seconds between repeated 2FA push notifications.
    twofa_notify_cooldown_s: int = 600

    # --- In-app update (app/routes/app_update.py) ---
    # GitHub repo "owner/name" queried by /app/latest.
    github_repo: str = "whtis/ibkr-mobile"
    # Optional token for reading releases of a private repo (empty = public).
    github_token: str = ""
    # If set, /app/latest rewrites the APK URL through this base (a Cloudflare
    # Worker /apk proxy) so the phone downloads from CF instead of GitHub's
    # China-throttled CDN. e.g. "https://fcm.your-domain.com".
    apk_proxy_base: str = ""


settings = Settings()
