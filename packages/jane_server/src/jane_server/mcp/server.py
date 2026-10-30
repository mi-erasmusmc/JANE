"""MCP server (MCPServer / FastMCP, Streamable HTTP), mounted into the FastAPI app.

Tools: suggest_journals, suggest_authors, find_similar_papers (open, per-IP rate limited);
find_author_emails (API-key gated + stricter limits, keeps disambiguation). All delegate to
the shared SearchService. See IMPLEMENTATION_NOTES.md section 8.
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque

from jane_core.filters import Filters
from jane_core.search_service import SearchService
from mcp.server.mcpserver import Context, MCPServer

# Per-IP rate limits (requests per rolling 60 s window).
CORE_RATE_LIMIT = 60
EMAIL_RATE_LIMIT = 10
WINDOW_SECONDS = 60.0


class RateLimiter:
    """Minimal in-memory sliding-window per-IP limiter (per logical bucket)."""

    def __init__(self) -> None:
        self._hits: dict[tuple[str, str], deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, bucket: str, ip: str, limit: int) -> bool:
        now = time.monotonic()
        key = (bucket, ip)
        with self._lock:
            hits = self._hits[key]
            while hits and hits[0] <= now - WINDOW_SECONDS:
                hits.popleft()
            if len(hits) >= limit:
                return False
            hits.append(now)
            return True


def _client_ip(ctx: Context) -> str:
    headers = {k.lower(): v for k, v in (ctx.headers or {}).items()}
    fwd = headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return headers.get("x-real-ip", "unknown")


def _api_key(ctx: Context) -> str:
    headers = {k.lower(): v for k, v in (ctx.headers or {}).items()}
    return headers.get("x-api-key", "")


class RateLimitExceeded(Exception):
    pass


def build_mcp(search_service: SearchService, email_api_key: str = "") -> MCPServer:
    """Return the configured MCPServer bound to the shared SearchService."""
    mcp = MCPServer(
        name="JANE",
        instructions=(
            "JANE (Journal/Author Name Estimator) finds PubMed journals, authors and "
            "similar papers for a pasted title/abstract. Paste the manuscript text; JANE "
            "ranks recent PubMed journals and authors by how well their articles match."
        ),
    )
    limiter = RateLimiter()

    def _guard(ctx: Context, bucket: str, limit: int) -> None:
        if not limiter.check(bucket, _client_ip(ctx), limit):
            raise RateLimitExceeded(
                f"Rate limit exceeded ({limit} requests per minute). Please slow down."
            )

    @mcp.tool(
        description=(
            "Suggest the PubMed journals whose recent articles are most similar to the "
            "given manuscript title/abstract. Returns journals ranked by a confidence "
            "score (summed similarity, normalized to probabilities)."
        )
    )
    def suggest_journals(text: str, ctx: Context) -> list[dict]:
        _guard(ctx, "core", CORE_RATE_LIMIT)
        results = search_service.suggest_journals(text, Filters())
        return [j.model_dump() for j in results]

    @mcp.tool(
        description=(
            "Suggest the authors whose recent PubMed articles are most similar to the "
            "given manuscript title/abstract. Returns authors ranked by confidence score."
        )
    )
    def suggest_authors(text: str, ctx: Context) -> list[dict]:
        _guard(ctx, "core", CORE_RATE_LIMIT)
        results = search_service.suggest_authors(text, Filters())
        return [a.model_dump() for a in results]

    @mcp.tool(
        description=(
            "Find the PubMed papers most similar to the given manuscript title/abstract. "
            "Returns up to `count` papers (pmid, title, authors, journal, year, score)."
        )
    )
    def find_similar_papers(text: str, ctx: Context, count: int = 20) -> list[dict]:
        _guard(ctx, "core", CORE_RATE_LIMIT)
        results = search_service.find_similar_papers(text, Filters(), count=count)
        return [p.model_dump() for p in results]

    @mcp.tool(
        description=(
            "Find e-mail addresses for an author of recent PubMed papers. REQUIRES an "
            "X-API-Key header. To disambiguate, you MUST provide `coauthors` (names of "
            "co-authors on the author's papers); without co-authors no results are "
            "returned. Returns e-mail addresses with the year and pmid where each was found."
        )
    )
    def find_author_emails(author: str, coauthors: list[str], ctx: Context) -> list[dict]:
        if not email_api_key or _api_key(ctx) != email_api_key:
            raise PermissionError("find_author_emails requires a valid X-API-Key header.")
        _guard(ctx, "email", EMAIL_RATE_LIMIT)
        results = search_service.find_author_emails(author, coauthors)
        return [e.model_dump() for e in results]

    return mcp
