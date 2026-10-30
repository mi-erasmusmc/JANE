"""Fetch + join journal-level metadata (NOT stored in SQLite). See NOTES section 4.

Sources (join by ISSN, minus Eigenfactor which is removed):
  - DOAJ open access:      https://doaj.org/csv
  - PMC jlist (pmcmonths): https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv
  - Medline-indexed IDs:   NCBI E-utilities (needs API key + polite rate limiting)
  - J_Medline (abbr/ISSN): FTP ftp.ncbi.nih.gov/pubmed/.J
  - NIHPA flag derived during indexing.
"""


def load_journal_metadata():
    """Download sources and return an ISSN -> metadata mapping. TODO."""
    raise NotImplementedError
