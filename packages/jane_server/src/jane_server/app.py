"""FastAPI application: mounts web UI, REST API, admin, and the MCP endpoint.

The MCP server (Streamable HTTP) is mounted into this same app so it shares the
SearchService + Tantivy handle (no second index copy). See MODERNIZATION_PLAN.md sections 3-4,
IMPLEMENTATION_NOTES.md section 8.
"""


def create_app():
    """Build the FastAPI app: lifespan opens the index, include routers, mount MCP. TODO."""
    raise NotImplementedError
