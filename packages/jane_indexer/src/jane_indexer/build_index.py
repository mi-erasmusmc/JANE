"""Build the Tantivy index from included citations.

Uses jane_core.schema for fields and jane_core.text.normalize_to_text for tokenization so
the write path matches the query path exactly. Ports JournalIndexer.createDocument (minus
Eigenfactor/ai/airank). See MODERNIZATION_PLAN.md section 4.
"""

from __future__ import annotations

import os

import tantivy
from jane_core import schema as S
from jane_core.text import normalize_to_text

from .inclusion import display_journal_name, is_included


def extract_email_from_affiliation(affiliation: str | None) -> str | None:
    """Port of MedlineCitationTools.extractEMailFromAffiliation (scan from the end)."""
    if not affiliation:
        return None
    at_sign = before = after = False
    i = len(affiliation)
    while i > 0:
        i -= 1
        ch = affiliation[i]
        if ch.isalnum():
            if at_sign:
                before = True
            else:
                after = True
        elif ch == "@":
            at_sign = True
        elif ch not in ".-_":
            i += 1
            break
    if at_sign and before and after:
        candidate = affiliation[i:]
        return candidate.removesuffix(".")
    return None


def _first_email(citation):
    """Return (email, author) from the first non-empty affiliation, as in createDocument."""
    for i, affiliation in enumerate(citation.affiliations):
        if affiliation:
            author = citation.authors[i] if i < len(citation.authors) else ""
            email = extract_email_from_affiliation(affiliation)
            if email:
                return email, author
            return "", ""
    return "", ""


def _unique(seq):
    seen = set()
    out = []
    for item in seq:
        if item and item not in seen:
            seen.add(item)
            out.append(item)
    return out


def make_document(citation, journal_metadata) -> tantivy.Document | None:
    journal = display_journal_name(citation.journal_name, citation.journal_medline_abbr)
    if not journal:
        return None
    doc = tantivy.Document()
    doc.add_text(S.FIELD_TEXT, normalize_to_text(f"{citation.title}\n{citation.abstract}"))
    doc.add_integer(S.FIELD_PMID, int(citation.pmid))
    doc.add_text(S.FIELD_TITLE, citation.title)
    doc.add_text(S.FIELD_JOURNAL, journal)

    for ptype in citation.publication_types:
        if ptype:
            doc.add_text(S.FIELD_PUBLICATION_TYPE, ptype)

    authors = _unique(citation.authors)
    for author in authors:
        doc.add_text(S.FIELD_AUTHORS, author)
    doc.add_text(S.FIELD_AUTHORS_STORED, "\n".join(authors))

    doc.add_text(S.FIELD_LANGUAGE, citation.language)

    try:
        year = int(citation.year)
    except (TypeError, ValueError):
        year = 0
    doc.add_integer(S.FIELD_YEAR, year)

    doc.add_boolean(S.FIELD_RECENT, bool(citation.recent))

    openaccess, medlineindexed, pmcmonths = journal_metadata.lookup(
        citation.issn, citation.issn_linking
    )
    doc.add_boolean(S.FIELD_OPEN_ACCESS, openaccess)
    doc.add_boolean(S.FIELD_MEDLINE_INDEXED, medlineindexed)
    doc.add_boolean(S.FIELD_NIHPA, False)  # no upstream source; default False
    doc.add_integer(S.FIELD_PMC_MONTHS, int(pmcmonths))

    email, email_author = _first_email(citation)
    if email:
        doc.add_text(S.FIELD_EMAIL, email)
    if email_author:
        doc.add_text(S.FIELD_EMAIL_AUTHOR, email_author)

    doc.add_text(S.FIELD_ISSN, citation.issn or "")
    doc.add_text(S.FIELD_JOURNAL_ABBR, citation.journal_medline_abbr or citation.journal_abbr or "")
    return doc


def build_index(
    citations,
    journal_metadata,
    out_dir: str,
    valid_journals: set[str] | None = None,
    heap_size: int = 1_000_000_000,
    threads: int = 1,
) -> int:
    """Write a fresh Tantivy index to out_dir. Returns the number of documents indexed.

    Citations are filtered with inclusion.is_included (abstract, legal pub type, valid
    journal). If ``valid_journals`` is None the per-journal minimum is not enforced here
    (caller is expected to have pre-filtered).
    """
    os.makedirs(out_dir, exist_ok=True)
    index = tantivy.Index(S.build_schema(), path=out_dir)
    writer = index.writer(heap_size, threads)
    valid = valid_journals if valid_journals is not None else None
    count = 0
    for citation in citations:
        if valid is not None:
            if not is_included(citation, valid):
                continue
        elif not citation.abstract or not citation.journal_name:
            continue
        doc = make_document(citation, journal_metadata)
        if doc is None:
            continue
        writer.add_document(doc)
        count += 1
    writer.commit()
    index.reload()
    return count
