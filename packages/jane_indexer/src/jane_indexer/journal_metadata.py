"""Fetch + join journal-level metadata (NOT stored in SQLite). See NOTES section 4.

Sources (join by ISSN; Eigenfactor removed entirely):
  - DOAJ open access:        https://doaj.org/csv
  - PMC jlist (pmcmonths):   https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv
  - Medline-indexed ISSNs:   NCBI E-utilities nlmcatalog "currentlyindexed"
                             (esearch -> UIDs -> esummary -> ISSNList). Needs polite rate
                             limiting; an API key lifts the 3 req/s cap.

J_Medline is no longer needed: the SQLite already carries journal title/abbrev/ISSN, and
nlmcatalog esummary returns ISSNs directly. NIHPA has no upstream source and defaults False.
"""

from __future__ import annotations

import csv
import io
import re
import time
from dataclasses import dataclass, field

import httpx
from jane_core.schema import PMC_MONTHS_UNKNOWN

DOAJ_URL = "https://doaj.org/csv"
PMC_JLIST_URL = "https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv"
EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"

_ID_RE = re.compile(r"<Id>(\d+)</Id>")
_COUNT_RE = re.compile(r"<Count>(\d+)</Count>")
_ISSN_RE = re.compile(r"<issn>([0-9Xx\-]+)</issn>")


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


def fetch_medline_indexed_issns(
    client: httpx.Client, api_key: str | None, email: str | None, batch_size: int = 500
) -> set[str]:
    """esearch nlmcatalog 'currentlyindexed' -> UIDs -> esummary -> all ISSNs."""
    common = {}
    if api_key:
        common["api_key"] = api_key
    if email:
        common["email"] = email

    uids: list[str] = []
    retstart = 0
    total = None
    while total is None or retstart < total:
        params = {
            "db": "nlmcatalog",
            "term": "currentlyindexed",
            "retmax": str(batch_size),
            "retstart": str(retstart),
            **common,
        }
        text = _get(client, f"{EUTILS}/esearch.fcgi", params=params).text
        if total is None:
            m = _COUNT_RE.search(text)
            total = int(m.group(1)) if m else 0
        page = _ID_RE.findall(text)
        if not page:
            break
        uids.extend(page)
        retstart += batch_size
        _sleep(api_key)

    issns: set[str] = set()
    for start in range(0, len(uids), batch_size):
        chunk = uids[start : start + batch_size]
        params = {"db": "nlmcatalog", "id": ",".join(chunk), "version": "2.0", **common}
        text = _get(client, f"{EUTILS}/esummary.fcgi", params=params).text
        issns.update(_ISSN_RE.findall(text))
        _sleep(api_key)
    return issns


def _sleep(api_key: str | None) -> None:
    # NCBI allows 10 req/s with a key, 3 without. Stay polite.
    time.sleep(0.11 if api_key else 0.34)


def load_journal_metadata(
    api_key: str | None = None,
    email: str | None = None,
    offline: bool = False,
) -> JournalMetadata:
    """Download all sources and return the joined ISSN -> metadata structure.

    ``offline=True`` returns empty metadata (everything not-OA, not-indexed, pmc unknown):
    useful for tests and air-gapped runs.
    """
    if offline:
        return JournalMetadata()
    with httpx.Client(follow_redirects=True, headers={"User-Agent": "JANE-indexer"}) as client:
        open_access = parse_doaj(_get(client, DOAJ_URL).text)
        pmc = parse_pmc_jlist(_get(client, PMC_JLIST_URL).text)
        medline = fetch_medline_indexed_issns(client, api_key, email)
    return JournalMetadata(
        open_access_issns=open_access,
        medline_indexed_issns=medline,
        issn2pmcmonths=pmc,
    )
