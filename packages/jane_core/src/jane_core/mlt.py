"""MoreLikeThis term extraction (engine-agnostic). See IMPLEMENTATION_NOTES.md section 1.

Lucene MLT defaults with minTermFreq=1 (critical for a single pasted abstract). IDF is
read from the Tantivy index term statistics rather than a precomputed stats artifact.
"""

MAX_QUERY_TERMS = 25
MIN_TERM_FREQ = 1


def extract_terms(text: str, index) -> list[tuple[str, float]]:
    """Return weighted query terms [(term, boost), ...] for the pasted abstract.

    TODO: tokenize via jane_core.text.normalize, score candidate terms by input-TF * IDF
    (IDF from `index`), keep top MAX_QUERY_TERMS, return per-term boosts for a boolean OR.
    """
    raise NotImplementedError
