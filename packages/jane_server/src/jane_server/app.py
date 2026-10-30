"""FastAPI application: mounts web UI, REST API, admin, and the MCP endpoint.

The MCP server (Streamable HTTP) is mounted into this same app so it shares the
SearchService + Tantivy handle (no second index copy). See MODERNIZATION_PLAN.md sections 3-4,
IMPLEMENTATION_NOTES.md section 8.
"""

from __future__ import annotations

import contextlib
import logging

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .config import settings
from .index_manager import IndexManager
from .logging_metrics import configure_metrics_logging
from .mcp.server import build_mcp
from .routes import admin, api, web

try:
    from importlib.resources import files as _files

    _STATIC_DIR = str(_files("jane_server") / "static")
except Exception:  # noqa: BLE001  # pragma: no cover
    import os

    _STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")


class _ServiceProxy:
    """Delegates SearchService calls to the current IndexManager service.

    Keeps MCP tools (bound once at startup) pointed at the live service across reloads.
    """

    def __init__(self, manager: IndexManager) -> None:
        self._manager = manager

    def __getattr__(self, name: str):
        return getattr(self._manager.service, name)


def create_app() -> FastAPI:
    logging.basicConfig(level=logging.INFO)
    configure_metrics_logging(settings.metrics_log)
    manager = IndexManager(settings.index_path)

    mcp = build_mcp(_ServiceProxy(manager))
    mcp_app = mcp.streamable_http_app(streamable_http_path="/")

    @contextlib.asynccontextmanager
    async def lifespan(app: FastAPI):
        with contextlib.suppress(Exception):
            manager.open()
        async with mcp.session_manager.run():
            yield

    app = FastAPI(title="JANE", lifespan=lifespan)
    app.state.index_manager = manager

    app.include_router(api.router)
    app.include_router(admin.router)
    app.include_router(web.router)
    app.mount("/mcp", mcp_app)
    app.mount("/static", StaticFiles(directory=_STATIC_DIR), name="static")
    return app


app = create_app()
