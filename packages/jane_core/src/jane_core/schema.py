"""Tantivy index schema. See MODERNIZATION_PLAN.md section 4 (Index fields).

Fields: text, pmid, journal, authors[], ptyp, language, emailauthor, year, recent,
openaccess, medlineindexed, nihpa, pmcmonths. NO ai/airank.
"""


def build_schema():
    """Return the Tantivy Schema used by indexer and server. TODO: define fields per plan."""
    raise NotImplementedError
