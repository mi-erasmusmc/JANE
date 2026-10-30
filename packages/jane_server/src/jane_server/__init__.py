"""JANE server (lightweight box).

Serves the web UI (Jinja2), the REST API, and the MCP endpoint from one FastAPI app over
a single local Tantivy index (mmap). See MODERNIZATION_PLAN.md sections 3-5.
"""
