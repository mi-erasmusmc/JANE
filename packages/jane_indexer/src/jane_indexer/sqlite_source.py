"""Read citations from the existing PubMed.sqlite (read-only upstream, Stage 1 out of scope).

Single denormalized table ``pubmed_articles`` (confirmed live schema): authors /
affiliations / publication_types are newline-joined text columns, ``publication_date`` is a
proleptic-Gregorian ordinal (days since 0001-01-01, i.e. Python ``date.toordinal()``).

See IMPLEMENTATION_NOTES.md section 5.
"""

from __future__ import annotations

import datetime
import sqlite3
from dataclasses import dataclass


@dataclass
class Citation:
    pmid: int
    title: str
    abstract: str
    authors: list[str]
    affiliations: list[str]
    journal_name: str
    journal_abbr: str
    journal_medline_abbr: str
    issn: str
    issn_linking: str
    year: str
    publication_types: list[str]
    language: str
    recent: bool = False

    @property
    def journal_key(self) -> str:
        """Grouping key = MEDLINE title abbreviation (legacy journal.medlineTA), with fallbacks."""
        return self.journal_medline_abbr or self.journal_name or self.journal_abbr or ""


def _split(value: str | None) -> list[str]:
    if not value:
        return []
    return [part for part in value.split("\n")]


def years_ago(reference: datetime.date, years: int) -> datetime.date:
    """reference minus `years` years, clamped for Feb 29."""
    try:
        return reference.replace(year=reference.year - years)
    except ValueError:  # Feb 29 -> Feb 28
        return reference.replace(year=reference.year - years, day=28)


def window_ordinals(
    reference: datetime.date | None = None,
    span_years: int = 10,
    recent_years: int = 1,
    future_years: int = 10,
) -> tuple[int, int, int]:
    """Return (lower, recent, upper) publication_date ordinals for the index windows."""
    reference = reference or datetime.date.today()
    lower = years_ago(reference, span_years).toordinal()
    recent = years_ago(reference, recent_years).toordinal()
    upper = reference.replace(year=reference.year + future_years).toordinal()
    return lower, recent, upper


def connect(sqlite_path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(sqlite_path)
    conn.row_factory = sqlite3.Row
    return conn


def compute_journal_sets(
    conn: sqlite3.Connection, lower: int, recent: int, upper: int
) -> tuple[set[str], set[str]]:
    """Return (valid_journals, recent_journals).

    valid_journals  = journal keys with >= MIN_PMIDS_PER_JOURNAL papers in [lower, upper]
                      (counted over all papers in the window, matching legacy Jane.PMIDs).
    recent_journals = journal keys with >= 1 paper in [recent, upper].
    """
    from .inclusion import MIN_PMIDS_PER_JOURNAL

    def key_expr() -> str:
        return (
            "COALESCE(NULLIF(journal_medline_abbr, ''), NULLIF(journal_name, ''), "
            "NULLIF(journal_abbr, ''), '')"
        )

    valid: set[str] = set()
    counts = conn.execute(
        f"SELECT {key_expr()} AS k, COUNT(*) AS c FROM pubmed_articles "
        "WHERE publication_date BETWEEN ? AND ? GROUP BY k",
        (lower, upper),
    )
    for row in counts:
        if row["k"] and row["c"] >= MIN_PMIDS_PER_JOURNAL:
            valid.add(row["k"])

    recent_set: set[str] = set()
    recents = conn.execute(
        f"SELECT DISTINCT {key_expr()} AS k FROM pubmed_articles "
        "WHERE publication_date BETWEEN ? AND ?",
        (recent, upper),
    )
    for row in recents:
        if row["k"]:
            recent_set.add(row["k"])
    return valid, recent_set


def _row_to_citation(row: sqlite3.Row, recent_journals: set[str]) -> Citation:
    citation = Citation(
        pmid=row["pmid"],
        title=row["title"] or "",
        abstract=row["abstract"] or "",
        authors=_split(row["authors"]),
        affiliations=_split(row["affiliations"]),
        journal_name=row["journal_name"] or "",
        journal_abbr=row["journal_abbr"] or "",
        journal_medline_abbr=row["journal_medline_abbr"] or "",
        issn=row["issn"] or "",
        issn_linking=row["issn_linking"] or "",
        year=str(row["year"]) if row["year"] is not None else "",
        publication_types=_split(row["publication_types"]),
        language=row["language"] or "",
    )
    citation.recent = citation.journal_key in recent_journals
    return citation


def iter_citations(
    sqlite_path: str,
    reference: datetime.date | None = None,
    limit: int | None = None,
):
    """Yield Citation objects for papers in the 10-year window that have an abstract.

    The `recent` flag is set from the recent-journals set. Inclusion by publication type
    and minimum-papers-per-journal is applied downstream (see inclusion.is_included).
    """
    conn = connect(sqlite_path)
    try:
        lower, recent, upper = window_ordinals(reference)
        _, recent_journals = compute_journal_sets(conn, lower, recent, upper)
        sql = (
            "SELECT pmid, title, abstract, authors, affiliations, journal_name, "
            "journal_abbr, journal_medline_abbr, issn, issn_linking, year, "
            "publication_types, language FROM pubmed_articles "
            "WHERE publication_date BETWEEN ? AND ? "
            "AND abstract IS NOT NULL AND abstract != ''"
        )
        params: list = [lower, upper]
        if limit is not None:
            sql += " LIMIT ?"
            params.append(limit)
        cursor = conn.execute(sql, params)
        for row in cursor:
            yield _row_to_citation(row, recent_journals)
    finally:
        conn.close()
