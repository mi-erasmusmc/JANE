"""Text normalization — the shared analyzer. MUST be identical on index and query paths.

Reproduces the legacy MedlineAnalyzer (legacy/JaneServer/.../MedlineAnalyzer.java):
strip every char except [A-Za-z0-9.] to a space (dots preserved), lowercase, then drop
the 4 stopwords. No stemming. See IMPLEMENTATION_NOTES.md section 1.
"""

# Legacy analyzer constants — do NOT change without a full reindex.
STOPWORDS: frozenset[str] = frozenset({"of", "the", "and", "in"})


def normalize(text: str) -> list[str]:
    """Normalize raw text into the token list used for indexing and querying.

    TODO: strip [^A-Za-z0-9.] -> space (preserve dots), lowercase, split on whitespace,
    drop STOPWORDS. Feed the result to Tantivy's whitespace/raw tokenizer so index and
    query tokenization are guaranteed identical (IMPLEMENTATION_NOTES.md #0.1).
    """
    raise NotImplementedError
