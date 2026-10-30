"""MCP server (FastMCP, Streamable HTTP), mounted into the FastAPI app.

Tools: suggest_journals, suggest_authors, find_similar_papers (open, per-IP rate limited);
find_author_emails (API-key gated + stricter limits, keeps disambiguation). All delegate to
the shared SearchService. See IMPLEMENTATION_NOTES.md section 8.
"""


def build_mcp(search_service):
    """Return the configured FastMCP server bound to the shared SearchService. TODO."""
    raise NotImplementedError
