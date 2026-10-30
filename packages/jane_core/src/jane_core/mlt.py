"""MoreLikeThis term extraction (engine-agnostic). See IMPLEMENTATION_NOTES.md section 1.

Reproduces the term-selection behaviour of Lucene 3's MoreLikeThis as configured by the
legacy JaneSOAPServer (minTermFreq=1, maxQueryTerms=25, Lucene default minDocFreq=5):

  * tokenize the pasted abstract with the SHARED analyzer (jane_core.text.normalize),
  * score each candidate term by input term-frequency * IDF, with IDF read directly from
    the Tantivy index statistics (searcher.doc_freq) using Lucene's DefaultSimilarity idf,
  * drop terms below minTermFreq or minDocFreq,
  * keep the top maxQueryTerms terms, returned as (term, boost) for a weighted boolean OR.
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

    ``searcher`` is a Tantivy Searcher (provides ``doc_freq`` and ``num_docs``).
    """
    tokens = normalize(text)
    if not tokens:
        return []
    num_docs = searcher.num_docs
    if num_docs == 0:
        return []

    term_freqs = Counter(tokens)
    scored: list[tuple[str, float]] = []
    for term, tf in term_freqs.items():
        if tf < min_term_freq:
            continue
        df = searcher.doc_freq(field, term)
        if df < min_doc_freq or df == 0:
            continue
        score = tf * _idf(df, num_docs)
        scored.append((term, score))

    # Highest-scoring terms first; break ties deterministically by term for stable queries.
    scored.sort(key=lambda t: (-t[1], t[0]))
    return scored[:max_query_terms]
