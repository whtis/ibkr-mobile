"""Symbol search.

IBKR's reqMatchingSymbols is used as the primary source — it covers US/HK/Europe
and stays fresh with IBKR's contract universe. For Chinese keywords ("茅台",
"腾讯") and other cases where IBKR returns nothing, we fall back to Eastmoney's
unified suggest API which handles Chinese names and A-shares natively.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from .. import eastmoney
from ..auth import require_token
from ..ibkr import client
from ..models import SearchResult

router = APIRouter(prefix="/search", dependencies=[Depends(require_token)])

_PREFERRED_SEC_TYPES = ("STK", "ETF")
_KEPT_SEC_TYPES = _PREFERRED_SEC_TYPES + ("IND", "FUT")


def _has_cjk(s: str) -> bool:
    """True if s contains any non-ASCII char — used as a cheap proxy for
    "this is a Chinese/Japanese keyword that IBKR can not handle"."""
    return any(ord(c) > 127 for c in s)


async def _ibkr_search(q: str, limit: int) -> list[SearchResult]:
    ib = await client.ensure_connected()
    try:
        descriptions = await ib.reqMatchingSymbolsAsync(q)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(502, f"IBKR symbol search failed: {e}") from e
    descriptions = descriptions or []
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
            name=None,
        )
        (preferred if c.secType in _PREFERRED_SEC_TYPES else rest).append(item)
    return (preferred + rest)[:limit]


async def _eastmoney_search(q: str, limit: int) -> list[SearchResult]:
    raw = await eastmoney.search(q, limit)
    return [SearchResult(**r) for r in raw]


@router.get("", response_model=list[SearchResult])
async def search(
    q: str = Query(..., min_length=1, max_length=32, description="Partial symbol or company name"),
    limit: int = Query(10, ge=1, le=30),
) -> list[SearchResult]:
    q = q.strip()
    if not q:
        return []
    # CJK / non-ASCII queries skip IBKR entirely — reqMatchingSymbols cannot match them.
    if _has_cjk(q):
        return await _eastmoney_search(q, limit)
    ibkr = await _ibkr_search(q, limit)
    if ibkr:
        return ibkr
    # IBKR found nothing — try Eastmoney as fallback (covers some non-Chinese terms too).
    return await _eastmoney_search(q, limit)
