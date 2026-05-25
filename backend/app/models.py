from pydantic import BaseModel


class AccountSummary(BaseModel):
    account_id: str
    currency: str = "USD"
    net_liquidation: float = 0.0
    total_cash: float = 0.0
    buying_power: float = 0.0
    realized_pnl: float = 0.0
    unrealized_pnl: float = 0.0
    daily_pnl: float = 0.0


class Position(BaseModel):
    account: str
    symbol: str
    sec_type: str
    exchange: str
    currency: str
    position: float
    avg_cost: float
    market_price: float | None = None
    market_value: float | None = None
    unrealized_pnl: float | None = None
    realized_pnl: float | None = None
    daily_pnl: float | None = None


class ExtendedQuote(BaseModel):
    last: float | None = None
    prev_close: float | None = None
    change: float | None = None
    change_pct: float | None = None
    volume: float | None = None
    timestamp: str | None = None


class Quote(BaseModel):
    symbol: str
    last: float | None
    bid: float | None
    ask: float | None
    open: float | None = None
    high: float | None
    low: float | None
    close: float | None
    prev_close: float | None = None
    volume: float | None
    turnover: float | None = None
    change: float | None = None
    change_pct: float | None = None
    pre_market: ExtendedQuote | None = None
    post_market: ExtendedQuote | None = None
    timestamp: str


class Bar(BaseModel):
    time: int  # Unix epoch seconds (UTC)
    open: float
    high: float
    low: float
    close: float
    volume: float


class IntradayPoint(BaseModel):
    time: int            # epoch seconds
    price: float
    avg_price: float | None = None   # VWAP cumulative, computed server-side
    volume: float | None = None
    line_type: int = 0   # 0=Normal, 1=PreMarket, 2=PostMarket, 3=Overnight (Longbridge TradeSession)


class TradeTick(BaseModel):
    time: int            # epoch seconds
    price: float
    volume: float
    direction: str = ""  # "Buy", "Sell", "Neutral", or empty


class OptionExpiry(BaseModel):
    expiry: str          # YYYY-MM-DD


class OptionContract(BaseModel):
    symbol: str
    underlying: str
    expiry: str
    strike: float
    right: str           # "C" or "P"
    last: float | None = None
    change_pct: float | None = None
    iv: float | None = None
    delta: float | None = None
    gamma: float | None = None
    theta: float | None = None
    vega: float | None = None
    open_interest: int | None = None
    volume: int | None = None


class StaticInfo(BaseModel):
    symbol: str
    name_cn: str | None = None
    name_en: str | None = None
    name_hk: str | None = None
    exchange: str | None = None
    currency: str | None = None
    lot_size: int | None = None
    total_shares: int | None = None
    circulating_shares: int | None = None
    eps: float | None = None
    eps_ttm: float | None = None
    bps: float | None = None
    dividend_yield: float | None = None


class DepthLevel(BaseModel):
    position: int
    price: float
    volume: int
    order_num: int


class Depth(BaseModel):
    symbol: str
    bids: list[DepthLevel] = []
    asks: list[DepthLevel] = []
