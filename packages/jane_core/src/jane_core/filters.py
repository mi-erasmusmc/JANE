"""Structured search filters + legacy filter-string conversion.

Kept filters: language, type, openaccess, medlineindexed, nihpa, pmcmonths, recent.
(subset is dropped.) See IMPLEMENTATION_NOTES.md section 3.
"""


def parse_legacy_filter_string(s: str):
    """Convert a pipe-separated legacy filter string into structured Filters. TODO."""
    raise NotImplementedError
