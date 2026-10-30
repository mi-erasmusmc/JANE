"""Text normalization — the shared analyzer. MUST be identical on index and query paths.

Reproduces the legacy MedlineAnalyzer (legacy/JaneServer/.../MedlineAnalyzer.java):
strip every char except [A-Za-z0-9.] to a space (dots preserved), lowercase, then drop
the 4 stopwords. No stemming. See IMPLEMENTATION_NOTES.md section 1.

The normalized token list is joined with single spaces and fed to Tantivy's built-in
``whitespace`` tokenizer (which splits on whitespace only and does NOT lowercase), so the
index-time and query-time token streams are guaranteed identical.
"""

# Legacy analyzer constants — do NOT change without a full reindex.
STOPWORDS: frozenset[str] = frozenset({"of", "the", "and", "in"})

# Built-in Tantivy tokenizer used for the analyzed `text` field. normalize() output is
# already lowercased, so a case-preserving whitespace split is exactly right.
TEXT_TOKENIZER = "whitespace"


def _keep(ch: str) -> bool:
    # Mirrors Java Character.isLetterOrDigit(ch) || ch == '.'
    return ch.isalnum() or ch == "."


def normalize(text: str | None) -> list[str]:
    """Normalize raw text into the token list used for indexing and querying.

    Strips every char that is not alphanumeric or ``.`` to a space (dots preserved),
    lowercases, splits on whitespace, drops the 4 stopwords, and discards tokens that
    contain no alphanumeric character (e.g. a stray ``.``).
    """
    if not text:
        return []
    cleaned = "".join(ch if _keep(ch) else " " for ch in text).lower()
    tokens = []
    for token in cleaned.split():
        if token in STOPWORDS:
            continue
        if not any(c.isalnum() for c in token):
            continue
        tokens.append(token)
    return tokens


def normalize_to_text(text: str | None) -> str:
    """Normalize and re-join with single spaces, ready for the whitespace tokenizer."""
    return " ".join(normalize(text))
