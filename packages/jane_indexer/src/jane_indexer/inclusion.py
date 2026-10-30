"""Inclusion rules. Port the EXACT values from legacy/JaneServer/.../JournalIndexer.java.

Rules: last 10 years, must have abstract, exclude ~30 publication types, min 20
papers/journal in the last year, journal-name length cap (~80), recent = last 1 year.
See IMPLEMENTATION_NOTES.md section 1.
"""

MIN_PMIDS_PER_JOURNAL = 20

# TODO: paste the exact excluded publication-type list from JournalIndexer.java.
EXCLUDED_PUBLICATION_TYPES: frozenset[str] = frozenset()


def is_included(citation) -> bool:
    """True if the citation passes the inclusion criteria. TODO."""
    raise NotImplementedError
