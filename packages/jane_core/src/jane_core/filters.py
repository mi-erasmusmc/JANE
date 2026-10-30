"""Structured search filters + legacy filter-string conversion.

Kept filters: language, type, openaccess, medlineindexed, nihpa, pmcmonths, recent.
(subset is dropped.) See IMPLEMENTATION_NOTES.md section 3.

Legacy syntax is pipe-separated ``key=value`` (plus the bare token ``recent``), e.g.
``language=eng|type=Journal Article|openaccess=true|pmcmonths=immediate|recent``.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# pmcmonths filter tokens -> inclusive (lower, upper) month range, mirroring the legacy
# JaneSOAPServer.parseFilter NumericRangeQuery bounds.
PMC_MONTHS_RANGES: dict[str, tuple[int, int]] = {
    "immediate": (0, 0),
    "inoneyear": (0, 12),
    "any": (0, 999),
}


@dataclass
class Filters:
    language: list[str] = field(default_factory=list)
    types: list[str] = field(default_factory=list)
    openaccess: bool | None = None
    medlineindexed: bool | None = None
    nihpa: bool | None = None
    pmcmonths: str | None = None  # one of PMC_MONTHS_RANGES keys
    recent: bool = False

    def is_empty(self) -> bool:
        return not (
            self.language
            or self.types
            or self.openaccess is not None
            or self.medlineindexed is not None
            or self.nihpa is not None
            or self.pmcmonths
            or self.recent
        )


def _as_bool(value: str) -> bool:
    return value.strip().lower() in {"true", "1", "yes"}


def parse_legacy_filter_string(s: str | None) -> Filters:
    """Convert a pipe-separated legacy filter string into structured Filters.

    Unknown keys and the dropped ``subset`` key are ignored. Repeated ``language=`` /
    ``type=`` entries accumulate (OR semantics), as in the old parseFilter.
    """
    filters = Filters()
    if not s:
        return filters
    for part in s.split("|"):
        part = part.strip()
        if not part:
            continue
        if part == "recent":
            filters.recent = True
            continue
        key, sep, value = part.partition("=")
        key = key.strip().lower()
        value = value.strip()
        if not sep:
            continue
        if key == "language":
            if value:
                filters.language.append(value)
        elif key == "type":
            if value:
                filters.types.append(value)
        elif key == "openaccess":
            filters.openaccess = _as_bool(value) if value else True
        elif key == "medlineindexed":
            filters.medlineindexed = _as_bool(value) if value else True
        elif key == "nihpa":
            filters.nihpa = _as_bool(value) if value else True
        elif key == "pmcmonths":
            if value in PMC_MONTHS_RANGES:
                filters.pmcmonths = value
        elif key == "recent":
            filters.recent = _as_bool(value) if value else True
        # subset= and anything else: ignored.
    return filters
