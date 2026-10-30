"""Admin endpoints. POST /admin/reload triggers index reopen after an S3 swap.

Protected by the admin API key. See IMPLEMENTATION_NOTES.md section 10.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from ..config import settings

router = APIRouter(prefix="/admin", tags=["admin"])


def require_admin_key(x_api_key: str = Header(default="")) -> None:
    if not settings.admin_api_key or x_api_key != settings.admin_api_key:
        raise HTTPException(status_code=401, detail="Valid admin X-API-Key required")


@router.post("/reload", dependencies=[Depends(require_admin_key)])
def reload_index(request: Request) -> dict:
    """Reopen the Tantivy index at the (possibly newly swapped) symlink target."""
    manager = request.app.state.index_manager
    manager.reload()
    return {"status": "reloaded", "index_path": manager.index_path}
