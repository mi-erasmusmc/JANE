"""Build the Tantivy index from included citations.

Uses jane_core.schema for fields and jane_core.text.normalize for tokenization so the
write path matches the query path exactly. See MODERNIZATION_PLAN.md section 4.
"""


def build_index(citations, journal_metadata, out_dir: str):
    """Write a fresh Tantivy index to out_dir. TODO."""
    raise NotImplementedError
