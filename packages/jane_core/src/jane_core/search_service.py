"""SearchService: the shared query orchestration used by REST, web UI, and MCP.

MLT query + filters -> top-k=50 scored papers -> aggregate by journal/author (sum BM25
scores) -> normalize to probabilities (verbatim port of the legacy
computeJournalScores / computeAuthorScores / normalizeScores). See MODERNIZATION_PLAN.md section 4.

Two distinct normalizations are preserved (IMPLEMENTATION_NOTES.md section 1):
  1. per-result: divide every hit score by the max score if it exceeds 1 (legacy `normalize`),
  2. aggregation: divide each journal/author sum by the total sum -> probabilities.
"""

from __future__ import annotations

import re
from dataclasses import replace

from tantivy import FieldType, Occur, Query

from .filters import PMC_MONTHS_RANGES, Filters
from .mlt import MIN_DOC_FREQ, extract_terms
from .models import Author, EMail, Journal, Paper
from .schema import (
    FIELD_AUTHORS,
    FIELD_AUTHORS_STORED,
    FIELD_EMAIL,
    FIELD_EMAIL_AUTHOR,
    FIELD_ISSN,
    FIELD_JOURNAL,
    FIELD_JOURNAL_ABBR,
    FIELD_LANGUAGE,
    FIELD_MEDLINE_INDEXED,
    FIELD_NIHPA,
    FIELD_OPEN_ACCESS,
    FIELD_PMC_MONTHS,
    FIELD_PMID,
    FIELD_PUBLICATION_TYPE,
    FIELD_RECENT,
    FIELD_TEXT,
    FIELD_TITLE,
    FIELD_YEAR,
)

TOP_K = 50
MAX_RESULT_SIZE = 100
# Legacy getEMails collected the top 100 matching documents.
MAX_EMAIL_DOCS = 100

_WORD = re.compile(r"[0-9a-z]+")


def _default_tokens(s: str) -> list[str]:
    """Approximate Tantivy's built-in ``default`` tokenizer (lowercase, split on non-alnum)."""
    return _WORD.findall(s.lower())


class SearchService:
    """Stateless over a Tantivy index; one instance shared by all interfaces."""

    def __init__(self, index, min_doc_freq: int = MIN_DOC_FREQ):
        self.index = index
        self.schema = index.schema
        self.min_doc_freq = min_doc_freq

    # ---- public API ---------------------------------------------------------

    def suggest_journals(self, text: str, filters: Filters | None = None) -> list[Journal]:
        if not text:
            return []
        # Legacy getJournals always appends "|recent": only currently-active journals.
        filters = replace(filters or Filters(), recent=True)
        searcher, hits = self._search(text, filters, TOP_K)
        by_name: dict[str, Journal] = {}
        for score, addr in hits:
            doc = searcher.doc(addr)
            name = doc.get_first(FIELD_JOURNAL) or ""
            journal = by_name.get(name)
            if journal is None:
                journal = Journal(
                    name=name,
                    openaccess=doc.get_first(FIELD_OPEN_ACCESS),
                    medlineindexed=doc.get_first(FIELD_MEDLINE_INDEXED),
                    pmcmonths=doc.get_first(FIELD_PMC_MONTHS),
                    issn=doc.get_first(FIELD_ISSN) or "",
                    journalabbr=doc.get_first(FIELD_JOURNAL_ABBR) or "",
                )
                by_name[name] = journal
            journal.papers.append(self._paper(doc, score))
            journal.score += score
        journals = sorted(by_name.values(), key=lambda j: -j.score)
        _normalize_scores(journals)
        return journals[:MAX_RESULT_SIZE]

    def suggest_authors(self, text: str, filters: Filters | None = None) -> list[Author]:
        if not text:
            return []
        filters = filters or Filters()
        searcher, hits = self._search(text, filters, TOP_K)
        by_name: dict[str, Author] = {}
        for score, addr in hits:
            doc = searcher.doc(addr)
            paper = self._paper(doc, score)
            for name in _split_authors(doc.get_first(FIELD_AUTHORS_STORED)):
                author = by_name.get(name)
                if author is None:
                    author = Author(name=name)
                    by_name[name] = author
                author.papers.append(paper)
                author.score += score
        authors = sorted(by_name.values(), key=lambda a: -a.score)
        _normalize_scores(authors)
        return authors[:MAX_RESULT_SIZE]

    def find_similar_papers(
        self,
        text: str,
        filters: Filters | None = None,
        count: int = 20,
        offset: int = 0,
    ) -> list[Paper]:
        if not text:
            return []
        filters = filters or Filters()
        searcher, hits = self._search(text, filters, offset + count)
        papers = [self._paper(searcher.doc(addr), score) for score, addr in hits]
        return papers[offset : offset + count]

    def find_author_emails(
        self,
        author: str,
        coauthors: list[str] | None = None,
        filters: Filters | None = None,
    ) -> list[EMail]:
        # requireDisambiguationForEMail: coauthors needed to disambiguate (NOTES section 1).
        if not author:
            return []
        searcher = self.index.searcher()
        author_q = self._text_match(FIELD_EMAIL_AUTHOR, author)
        if author_q is None:
            return []
        clauses: list[tuple[Occur, Query]] = [(Occur.Must, author_q)]
        if coauthors:
            coauthor_subs = []
            for coauthor in coauthors:
                q = self._text_match(FIELD_AUTHORS, coauthor)
                if q is not None:
                    coauthor_subs.append((Occur.Should, q))
            if coauthor_subs:
                clauses.append((Occur.Must, Query.boolean_query(coauthor_subs)))
        query = Query.boolean_query(clauses)
        result = searcher.search(query, MAX_EMAIL_DOCS)
        emails: list[EMail] = []
        for score, addr in result.hits:
            if score <= 0:
                continue
            doc = searcher.doc(addr)
            email = doc.get_first(FIELD_EMAIL)
            if not email:
                continue
            emails.append(
                EMail(
                    email=email,
                    year=_as_str(doc.get_first(FIELD_YEAR)),
                    pmid=_as_str(doc.get_first(FIELD_PMID)),
                )
            )
        # Most recent first, then highest pmid (mirrors legacy EMail.compareTo).
        emails.sort(key=lambda e: (e.year, e.pmid), reverse=True)
        return emails

    # ---- internals ----------------------------------------------------------

    def _search(self, text: str, filters: Filters, k: int):
        searcher = self.index.searcher()
        mlt = self._mlt_query(searcher, text)
        if mlt is None:
            return searcher, []
        query = self._with_filters(mlt, filters)
        hits = searcher.search(query, max(k, 1)).hits
        if hits:
            max_score = hits[0][0]
            if max_score > 1:
                hits = [(score / max_score, addr) for score, addr in hits]
        return searcher, hits

    def _mlt_query(self, searcher, text: str):
        terms = extract_terms(text, searcher, min_doc_freq=self.min_doc_freq)
        if not terms:
            return None
        clauses = [
            (
                Occur.Should,
                Query.boost_query(Query.term_query(self.schema, FIELD_TEXT, term), float(boost)),
            )
            for term, boost in terms
        ]
        return Query.boolean_query(clauses)

    def _with_filters(self, mlt: Query, filters: Filters) -> Query:
        clauses: list[tuple[Occur, Query]] = [(Occur.Must, mlt)]
        clauses.extend(self._filter_clauses(filters))
        if len(clauses) == 1:
            return mlt
        return Query.boolean_query(clauses)

    def _filter_clauses(self, f: Filters) -> list[tuple[Occur, Query]]:
        clauses: list[tuple[Occur, Query]] = []
        if f.language:
            subs = [
                (Occur.Should, Query.term_query(self.schema, FIELD_LANGUAGE, lang))
                for lang in f.language
            ]
            clauses.append((Occur.Must, Query.boolean_query(subs)))
        if f.types:
            subs = []
            for t in f.types:
                q = self._text_match(FIELD_PUBLICATION_TYPE, t)
                if q is not None:
                    subs.append((Occur.Should, q))
            if subs:
                clauses.append((Occur.Must, Query.boolean_query(subs)))
        if f.openaccess is not None:
            clauses.append(
                (Occur.Must, Query.term_query(self.schema, FIELD_OPEN_ACCESS, f.openaccess))
            )
        if f.medlineindexed is not None:
            clauses.append(
                (Occur.Must, Query.term_query(self.schema, FIELD_MEDLINE_INDEXED, f.medlineindexed))
            )
        if f.nihpa is not None:
            clauses.append((Occur.Must, Query.term_query(self.schema, FIELD_NIHPA, f.nihpa)))
        if f.recent:
            clauses.append((Occur.Must, Query.term_query(self.schema, FIELD_RECENT, True)))
        if f.pmcmonths and f.pmcmonths in PMC_MONTHS_RANGES:
            lo, hi = PMC_MONTHS_RANGES[f.pmcmonths]
            clauses.append(
                (
                    Occur.Must,
                    Query.range_query(
                        self.schema, FIELD_PMC_MONTHS, FieldType.Integer, lo, hi, True, True
                    ),
                )
            )
        return clauses

    def _text_match(self, field: str, value: str) -> Query | None:
        tokens = _default_tokens(value)
        if not tokens:
            return None
        if len(tokens) == 1:
            return Query.term_query(self.schema, field, tokens[0])
        return Query.phrase_query(self.schema, field, tokens)

    def _paper(self, doc, score: float) -> Paper:
        return Paper(
            pmid=_as_str(doc.get_first(FIELD_PMID)),
            title=doc.get_first(FIELD_TITLE) or "",
            authors=_split_authors(doc.get_first(FIELD_AUTHORS_STORED)),
            journal=doc.get_first(FIELD_JOURNAL) or "",
            year=_as_str(doc.get_first(FIELD_YEAR)),
            score=score,
        )


def _split_authors(value) -> list[str]:
    if not value:
        return []
    return [a for a in value.split("\n") if a]


def _as_str(value) -> str:
    return "" if value is None else str(value)


def _normalize_scores(items) -> None:
    total = sum(item.score for item in items)
    if total <= 0:
        return
    for item in items:
        item.score = item.score / total
