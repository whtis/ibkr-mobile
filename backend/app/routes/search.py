"""Symbol search via IBKR's reqMatchingSymbols.

This is fuzzy / partial-symbol search ("appl" -> AAPL, "tesla" -> TSLA,
"700" -> Tencent on SEHK). Longbridge SDK does not expose an equivalent;
it only has static_info(symbols=) which requires the caller to already
know the exact symbol.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from ..auth import require_token
from ..ibkr import client
from ..models import SearchResult

router = APIRouter(prefix="/search", dependencies=[Depends(require_token)])

# Sec types we surface in search results, in display-priority order.
_PREFERRED_SEC_TYPES = ("STK", "ETF")
_KEPT_SEC_TYPES = _PREFERRED_SEC_TYPES + ("IND", "FUT")


@router.get("", response_model=list[SearchResult])
async def search(
    q: str = Query(..., min_length=1, max_length=32, description="Partial symbol or company name"),
    limit: int = Query(10, ge=1, le=30),
) -> list[SearchResult]:
    q = q.strip()
    if not q:
        return []
    ib = await client.ensure_connected()
    try:
        descriptions = await ib.reqMatchingSymbolsAsync(q)
    except Exception as e:  # noqa: BLE001 — surface as 502 to the client
        raise HTTPException(502, f"IBKR symbol search failed: {e}") from e
    descriptions = descriptions or []

    # Split by sec_type — STK/ETF first, then other kept types — preserve order within bucket.
    preferred: list[SearchResult] = []
    rest: list[SearchResult] = []
    for d in descriptions:
        c = getattr(d, "contract", None)
        if c is None or not c.symbol:
            continue
        if c.secType not in _KEPT_SEC_TYPES:
            continue
        item = SearchResult(
            symbol=c.symbol,
            sec_type=c.secType,
            primary_exchange=(c.primaryExchange or None),
            currency=(c.currency or None),
            derivative_sec_types=list(getattr(d, "derivativeSecTypes", None) or []),
        )
        (preferred if c.secType in _PREFERRED_SEC_TYPES else rest).append(item)
    return (preferred + rest)[:limit]
