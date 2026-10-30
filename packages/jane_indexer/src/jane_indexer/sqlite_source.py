"""Read citations from the existing PubMed.sqlite (read-only upstream).

Schema: pubmed_articles + normalized medcit_* tables (authors in
medcit_art_authorlist_author, affiliations in ..._affinfo). Confirm the live schema
before coding. See IMPLEMENTATION_NOTES.md section 5.
"""


def iter_citations(sqlite_path: str):
    """Yield citations within the 10-year window. TODO."""
    raise NotImplementedError
