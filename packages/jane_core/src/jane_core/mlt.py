"""MoreLikeThis term extraction (engine-agnostic). See IMPLEMENTATION_NOTES.md section 1.

Reproduces the term-selection behaviour of Lucene 3's MoreLikeThis as configured by the
legacy JaneSOAPServer (minTermFreq=1, maxQueryTerms=25, Lucene default minDocFreq=5):

  * tokenize the pasted abstract with the SHARED analyzer (jane_core.text.normalize),
  * score each candidate term by input term-frequency * IDF, with IDF read directly from
    the Tantivy index statistics (searcher.doc_freq) using Lucene's DefaultSimilarity idf,
  * drop terms below minTermFreq or minDocFreq,
  * keep the top maxQueryTerms terms.

Crucially, the legacy ``initMLT`` never calls ``setBoost(true)``, so Lucene MoreLikeThis ran
with its default ``boost=false``: the input term-frequency * IDF score was used ONLY to
*select* which terms enter the query, never to weight them. Document ranking then came
entirely from Lucene's DefaultSimilarity, which weights every selected term by idf-squared
(idf in the query weight times idf in the field weight) and ignores the input term
frequency. We reproduce that here by selecting terms on tf*idf but returning each term's
**IDF** (not tf*idf) as its query boost: combined with Tantivy's own BM25 idf this yields the
legacy idf-squared discrimination, and — like legacy — a term repeated many times in the
pasted text is NOT given extra weight (which previously let common words dominate the query).

Returned as ``(term, boost)`` for a weighted boolean OR.
"""

from __future__ import annotations

import math
from collections import Counter

from .schema import FIELD_TEXT
from .text import normalize

MAX_QUERY_TERMS = 25
MIN_TERM_FREQ = 1
# Lucene MoreLikeThis default: ignore terms that occur in fewer than 5 documents.
MIN_DOC_FREQ = 5


def _idf(doc_freq: int, num_docs: int) -> float:
    # Lucene 3 DefaultSimilarity.idf: log(numDocs / (docFreq + 1)) + 1.0
    return math.log(num_docs / (doc_freq + 1)) + 1.0


def extract_terms(
    text: str,
    searcher,
    *,
    field: str = FIELD_TEXT,
    max_query_terms: int = MAX_QUERY_TERMS,
    min_term_freq: int = MIN_TERM_FREQ,
    min_doc_freq: int = MIN_DOC_FREQ,
) -> list[tuple[str, float]]:
    """Return weighted query terms ``[(term, boost), ...]`` for the pasted abstract.

    Terms are *selected* by input term-frequency * IDF (legacy Lucene MoreLikeThis), but the
    returned ``boost`` is the term's IDF alone, mirroring legacy ``boost=false`` ranking (see
    the module docstring). ``searcher`` is a Tantivy Searcher (provides ``doc_freq`` and
    ``num_docs``).
    """
    tokens = normalize(text)
    if not tokens:
        return []
    num_docs = searcher.num_docs
    if num_docs == 0:
        return []

    term_freqs = Counter(tokens)
    scored: list[tuple[str, float, float]] = []
    for term, tf in term_freqs.items():
        if tf < min_term_freq:
            continue
        df = searcher.doc_freq(field, term)
        if df < min_doc_freq or df == 0:
            continue
        idf = _idf(df, num_docs)
        # Select on tf*idf (legacy term selection); weight on idf alone (legacy boost=false).
        scored.append((term, tf * idf, idf))

    # Highest-scoring terms first; break ties deterministically by term for stable queries.
    scored.sort(key=lambda t: (-t[1], t[0]))
    return [(term, idf) for term, _selection_score, idf in scored[:max_query_terms]]
