"""Eastmoney unified suggest API — used as fallback when IBKR's reqMatchingSymbols
returns no results. Handles Chinese keywords ("茅台", "腾讯", "阿里巴巴") which
IBKR cannot match, and covers A-shares, HK, US, UK markets.

This is an unofficial public endpoint of eastmoney.com — no auth required, no
documented SLA. Failures are logged and surfaced as empty results, not exceptions,
so the /search route stays correct even if Eastmoney is down.
"""
from __future__ import annotations

import logging

import httpx

log = logging.getLogger(__name__)

_URL = "https://searchapi.eastmoney.com/api/suggest/get"
_HEADERS = {
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://quote.eastmoney.com/",
}

# Eastmoney market code (QuoteID prefix) -> (IBKR primary_exchange, currency).
# Codes documented from observation; QuoteID is of the form "<prefix>.<symbol>".
_MARKET_MAP: dict[str, tuple[str, str]] = {
    "0":   ("SZSE",   "CNH"),  # 深A — IBKR Stock Connect Northbound uses CNH
    "1":   ("SHSE",   "CNH"),  # 沪A
    "105": ("NASDAQ", "USD"),  # NASDAQ
    "106": ("NYSE",   "USD"),  # NYSE
    "107": ("AMEX",   "USD"),  # NYSE American (Amex)
    "116": ("SEHK",   "HKD"),  # 港股
    "155": ("LSE",    "GBP"),  # London Stock Exchange
}

# SecurityTypeName values to filter out — we want tradeable stocks/ETFs.
_SKIP_TYPES = {"指数", "债券", "外汇", "期货"}


async def search(q: str, limit: int = 10) -> list[dict]:
    """Search Eastmoney for q. Returns up to `limit` normalized dicts ready to
    feed into SearchResult(**d).
    """
    try:
        async with httpx.AsyncClient(timeout=8.0, headers=_HEADERS) as cl:
            r = await cl.get(_URL, params={"input": q, "type": "14", "count": str(limit * 2)})
            r.raise_for_status()
            data = r.json()
    except Exception as e:
        log.warning("eastmoney search failed for %r: %s", q, e)
        return []

    raw = (data.get("QuotationCodeTable") or {}).get("Data") or []
    out: list[dict] = []
    for x in raw:
        type_name = x.get("SecurityTypeName") or ""
        if type_name in _SKIP_TYPES:
            continue
        quote_id = x.get("QuoteID") or ""
        if "." not in quote_id:
            continue
        prefix, code = quote_id.split(".", 1)
        mapping = _MARKET_MAP.get(prefix)
        if mapping is None:
            continue  # unmapped market (e.g. US OTC, Japan); skip rather than mislabel
        primary_exchange, currency = mapping
        symbol = x.get("Code") or code
        # IBKR uses HK symbols without leading zeros: Eastmoney returns "00700", IBKR wants "700".
        if primary_exchange == "SEHK" and symbol.isdigit():
            symbol = str(int(symbol))
        name = x.get("Name") or None
        sec_type = "ETF" if (name and "ETF" in name.upper()) else "STK"
        out.append({
            "symbol": symbol,
            "name": name,
            "primary_exchange": primary_exchange,
            "currency": currency,
            "sec_type": sec_type,
        })
        if len(out) >= limit:
            break
    return out
