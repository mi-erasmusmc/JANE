"""Fetch + join journal-level metadata (NOT stored in SQLite). See NOTES section 4.

Every source is a plain bulk-file download (no paged API calls, no API key needed), mirroring
the legacy JaneMasterIndexingScript. Sources are joined by ISSN (Eigenfactor removed entirely):
  - DOAJ open access:        https://doaj.org/csv
  - PMC jlist (pmcmonths):   https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv
  - Medline-indexed ISSNs:   NLM's J_Medline.txt journal list
                             https://ftp.ncbi.nlm.nih.gov/pubmed/J_Medline.txt

NIHPA has no upstream source and defaults False.
"""

from __future__ import annotations

import csv
import io
from dataclasses import dataclass, field

import httpx
from jane_core.schema import PMC_MONTHS_UNKNOWN

DOAJ_URL = "https://doaj.org/csv"
PMC_JLIST_URL = "https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv"
J_MEDLINE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/J_Medline.txt"


@dataclass
class JournalMetadata:
    open_access_issns: set[str] = field(default_factory=set)
    medline_indexed_issns: set[str] = field(default_factory=set)
    issn2pmcmonths: dict[str, int] = field(default_factory=dict)

    @staticmethod
    def _any(issns: set[str], *values: str) -> bool:
        return any(v and v in issns for v in values)

    def lookup(self, issn: str, issn_linking: str):
        """Return (openaccess, medlineindexed, pmcmonths) for a citation's ISSNs."""
        oa = self._any(self.open_access_issns, issn, issn_linking)
        mi = self._any(self.medline_indexed_issns, issn, issn_linking)
        pmc = self.issn2pmcmonths.get(issn)
        if pmc is None and issn_linking:
            pmc = self.issn2pmcmonths.get(issn_linking)
        if pmc is None:
            pmc = PMC_MONTHS_UNKNOWN
        return oa, mi, pmc


def _get(client: httpx.Client, url: str, **kwargs) -> httpx.Response:
    resp = client.get(url, timeout=120, **kwargs)
    resp.raise_for_status()
    return resp


def parse_doaj(text: str) -> set[str]:
    issns: set[str] = set()
    reader = csv.DictReader(io.StringIO(text))
    for row in reader:
        for col in ("Journal ISSN (print version)", "Journal EISSN (online version)"):
            value = (row.get(col) or "").strip()
            if value:
                issns.add(value)
    return issns


def parse_pmc_jlist(text: str) -> dict[str, int]:
    issn2months: dict[str, int] = {}
    reader = csv.DictReader(io.StringIO(text))
    for row in reader:
        delay = _parse_embargo(row.get("Release Delay (Embargo)", ""))
        if delay is None:
            continue
        for col in ("ISSN (print)", "ISSN (online)"):
            value = (row.get(col) or "").strip()
            if value:
                issn2months[value] = delay
    return issn2months


def _parse_embargo(text: str) -> int | None:
    text = (text or "").strip()
    if not text:
        return None
    if "Immediate" in text:
        return 0
    idx = text.find(" month")
    if idx == -1:
        return None
    try:
        return int(text[:idx].strip())
    except ValueError:
        return None


def parse_j_medline(text: str) -> set[str]:
    """Parse NLM's J_Medline.txt into the set of ISSNs of journals indexed in MEDLINE.

    The file is a flat list of records separated by dashed lines; each record carries an
    ``ISSN (Print):`` and an ``ISSN (Online):`` field (either may be blank).
    """
    issns: set[str] = set()
    for line in text.splitlines():
        if line.startswith(("ISSN (Print):", "ISSN (Online):")):
            value = line.split(":", 1)[1].strip()
            if value:
                issns.add(value)
    return issns


def fetch_medline_indexed_issns(client: httpx.Client) -> set[str]:
    """Download NLM's J_Medline.txt and return the ISSNs of MEDLINE-indexed journals."""
    return parse_j_medline(_get(client, J_MEDLINE_URL).text)


def load_journal_metadata(offline: bool = False) -> JournalMetadata:
    """Download all sources and return the joined ISSN -> metadata structure.

    Every source is a single bulk-file download; no API key is required. ``offline=True``
    returns empty metadata (everything not-OA, not-indexed, pmc unknown): useful for tests
    and air-gapped runs.
    """
    if offline:
        return JournalMetadata()
    with httpx.Client(follow_redirects=True, headers={"User-Agent": "JANE-indexer"}) as client:
        open_access = parse_doaj(_get(client, DOAJ_URL).text)
        pmc = parse_pmc_jlist(_get(client, PMC_JLIST_URL).text)
        medline = fetch_medline_indexed_issns(client)
    return JournalMetadata(
        open_access_issns=open_access,
        medline_indexed_issns=medline,
        issn2pmcmonths=pmc,
    )
