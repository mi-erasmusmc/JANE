"""Text normalization — the shared analyzer. MUST be identical on index and query paths.

Reproduces the legacy MedlineAnalyzer (legacy/JaneServer/.../MedlineAnalyzer.java):
strip every char except [A-Za-z0-9.] to a space (dots preserved), lowercase, then drop
the 4 stopwords. No stemming. See IMPLEMENTATION_NOTES.md section 1.

The normalized token list is joined with single spaces and fed to Tantivy's built-in
``whitespace`` tokenizer (which splits on whitespace only and does NOT lowercase), so the
index-time and query-time token streams are guaranteed identical.
"""

from __future__ import annotations

import re

# Legacy analyzer constants — do NOT change without a full reindex.
STOPWORDS: frozenset[str] = frozenset({"of", "the", "and", "in"})

# Built-in Tantivy tokenizer used for the analyzed `text` field. normalize() output is
# already lowercased, so a case-preserving whitespace split is exactly right.
TEXT_TOKENIZER = "whitespace"

# Query tokenizer: a quoted "phrase" (kept whole) or any run of non-space characters.
_re_query_token = re.compile(r'"[^"]*"|\S+')


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


# Query-syntax characters/operators preserved by normalize_query (everything else that is not
# alphanumeric or '.' becomes a space, exactly like the index-time analyzer).
_QUERY_OPERATORS = frozenset({"AND", "OR", "NOT"})


def _filter_chars(s: str) -> str:
    """Lowercase; map every char that is not alphanumeric or ``.`` to a space (dots preserved)."""
    return "".join(ch.lower() if _keep(ch) else " " for ch in s)


def normalize_query(text: str | None) -> str:
    """Normalize a keyword/boolean query for Tantivy's query parser.

    Reproduces legacy JANE's "keyword search" (the ``structured query`` mode), where Lucene's
    QueryParser parsed the query syntax while the MedlineAnalyzer normalized the term text.
    This preserves the query syntax -- quoted ``"phrases"``, ``(grouping)``, ``+required`` /
    ``-prohibited`` prefixes, and the boolean operators ``AND`` / ``OR`` / ``NOT`` (only when
    upper-case, as in Lucene) -- while passing all term text through the shared analyzer
    char-filter (lower-case, punctuation -> space, dots preserved) so query terms match the
    normalized index tokens. Unbalanced quotes/parentheses degrade to plain terms rather than
    raising.
    """
    if not text:
        return ""
    # Isolate parentheses so they tokenize separately from adjacent words.
    spaced = text.replace("(", " ( ").replace(")", " ) ")
    tokens = _re_query_token.findall(spaced)
    out: list[str] = []
    for tok in tokens:
        if tok in ("(", ")") or tok in _QUERY_OPERATORS:
            out.append(tok)
        elif len(tok) >= 2 and tok[0] == '"' and tok[-1] == '"':
            inner = _filter_chars(tok[1:-1]).strip()
            if inner:
                out.append(f'"{inner}"')
        else:
            prefix = tok[0] if tok[:1] in ("+", "-") else ""
            core = tok[1:] if prefix else tok
            normalized = _filter_chars(core).strip()
            if normalized:
                out.append(f"{prefix}{normalized}")
    return " ".join(out)
