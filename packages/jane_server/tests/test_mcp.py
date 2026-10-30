import asyncio

from jane_server.mcp.server import RateLimiter, build_mcp


class _StubService:
    def suggest_journals(self, text, filters):
        return []

    def suggest_authors(self, text, filters):
        return []

    def find_similar_papers(self, text, filters, count=20):
        return []

    def find_author_emails(self, author, coauthors):
        return []


def test_mcp_registers_expected_tools():
    mcp = build_mcp(_StubService())
    tools = asyncio.run(mcp.list_tools())
    names = {t.name for t in tools}
    assert names == {
        "suggest_journals",
        "suggest_authors",
        "find_similar_papers",
        "find_author_emails",
    }


def test_rate_limiter_enforces_window():
    rl = RateLimiter()
    assert all(rl.check("core", "1.2.3.4", 3) for _ in range(3))
    assert not rl.check("core", "1.2.3.4", 3)
    # Different IP has its own bucket.
    assert rl.check("core", "5.6.7.8", 3)
