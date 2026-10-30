"""Inclusion rules. Ported from legacy/JaneServer/.../JournalIndexer.java.

Rules (JournalIndexer):
  * citation must have a non-empty abstract,
  * citation must not have any excluded publication type,
  * its journal must have >= MIN_PMIDS_PER_JOURNAL papers in the (10-year) window,
  * journal display name capped at MAX_JOURNAL_LENGTH chars (else the MEDLINE abbrev),
  * ``recent`` = the journal published at least one paper in the last year.

See IMPLEMENTATION_NOTES.md section 1.
"""

MIN_PMIDS_PER_JOURNAL = 20
MAX_JOURNAL_LENGTH = 80

# Exact list ported verbatim from JournalIndexer.getIgnorePubTypes(). The source contains
# two historical quirks preserved for parity: "Technical Reportt" (sic) and "Addresses".
EXCLUDED_PUBLICATION_TYPES: frozenset[str] = frozenset(
    {
        "Comment",
        "Editorial",
        "News",
        "Historical Article",
        "Congresses",
        "Biography",
        "Newspaper Article",
        "Guideline",
        "Practice Guideline",
        "Interview",
        "Bibliography",
        "Legal Cases",
        "Lectures",
        "Consensus Development Conference",
        "Addresses",
        "Clinical Conference",
        "Patient Education Handout",
        "Directory",
        "Technical Reportt",
        "Festschrift",
        "Retraction of Publication",
        "Retracted Publication",
        "Duplicate Publication",
        "Scientific Integrity Review",
        "Published Erratum",
        "Consensus Development Conference, NIH",
        "Periodical Index",
        "Dictionary",
        "Legislation",
        "Government Publications",
    }
)


def has_legal_pub_type(publication_types) -> bool:
    """True unless any of the citation's publication types is excluded (JournalIndexer.legalPubType)."""
    for ptype in publication_types or ():
        if ptype in EXCLUDED_PUBLICATION_TYPES:
            return False
    return True


def is_included(citation, valid_journals: set[str]) -> bool:
    """True if the citation passes all inclusion criteria.

    ``valid_journals`` is the set of journal keys meeting MIN_PMIDS_PER_JOURNAL.
    """
    if not citation.abstract:
        return False
    if not citation.journal_name:
        return False
    if not has_legal_pub_type(citation.publication_types):
        return False
    return citation.journal_key in valid_journals


def display_journal_name(journal_name: str, medline_abbr: str | None) -> str:
    """Full title unless it exceeds MAX_JOURNAL_LENGTH chars, then the MEDLINE abbrev."""
    if journal_name and len(journal_name) > MAX_JOURNAL_LENGTH and medline_abbr:
        return medline_abbr
    return journal_name
