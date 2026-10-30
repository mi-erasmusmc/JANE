"""SearchService: the shared query orchestration used by REST, web UI, and MCP.

MLT query + filters -> top-k=50 scored papers -> aggregate by journal/author (sum BM25
scores) -> normalize to probabilities (verbatim port of the legacy
computeJournalScores / computeAuthorScores / normalizeScores). See MODERNIZATION_PLAN.md section 4.
"""

TOP_K = 50
MAX_RESULT_SIZE = 100


class SearchService:
    """Stateless over a Tantivy searcher; one instance shared by all interfaces."""

    def suggest_journals(self, text: str, filters=None):
        raise NotImplementedError

    def suggest_authors(self, text: str, filters=None):
        raise NotImplementedError

    def find_similar_papers(self, text: str, filters=None, count: int = 20, offset: int = 0):
        raise NotImplementedError

    def find_author_emails(self, author: str, coauthors=None, filters=None):
        # requireDisambiguationForEMail: coauthors needed to disambiguate (NOTES section 1).
        raise NotImplementedError
