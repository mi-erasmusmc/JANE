"""Tantivy index schema. See MODERNIZATION_PLAN.md section 4 (Index fields).

Fields: text, pmid, journal, authors, ptyp, language, emailauthor, year, recent,
openaccess, medlineindexed, nihpa, pmcmonths (+ stored-only display fields). NO ai/airank.

The analyzed `text` field uses the built-in ``whitespace`` tokenizer; callers feed it the
output of jane_core.text.normalize_to_text so index/query tokenization can never drift.
"""

from tantivy import SchemaBuilder

from .text import TEXT_TOKENIZER

# Field names (single source of truth, used by indexer + server).
FIELD_TEXT = "text"
FIELD_PMID = "pmid"
FIELD_TITLE = "title"
FIELD_JOURNAL = "journal"
FIELD_AUTHORS = "authors"
FIELD_AUTHORS_STORED = "authors_stored"
FIELD_EMAIL = "email"
FIELD_EMAIL_AUTHOR = "emailauthor"
FIELD_PUBLICATION_TYPE = "ptyp"
FIELD_LANGUAGE = "language"
FIELD_YEAR = "year"
FIELD_RECENT = "recent"
FIELD_OPEN_ACCESS = "openaccess"
FIELD_MEDLINE_INDEXED = "medlineindexed"
FIELD_NIHPA = "nihpa"
FIELD_PMC_MONTHS = "pmcmonths"
FIELD_ISSN = "issn"
FIELD_JOURNAL_ABBR = "journalabbr"

# PMC "unknown delay" sentinel (legacy used -1).
PMC_MONTHS_UNKNOWN = -1


def build_schema():
    """Return the Tantivy Schema shared by the indexer (write) and server (query)."""
    sb = SchemaBuilder()

    # Analyzed similarity field (MoreLikeThis target) + keyword/boolean search target.
    # index_option="position" so Tantivy's query parser can run phrase queries ("a b") here.
    sb.add_text_field(
        FIELD_TEXT, stored=False, tokenizer_name=TEXT_TOKENIZER, index_option="position"
    )

    # Identity / display.
    sb.add_integer_field(FIELD_PMID, stored=True, indexed=True, fast=True)
    sb.add_text_field(FIELD_TITLE, stored=True, tokenizer_name="raw", index_option="basic")

    # Journal: exact-match string (grouping + similar-in-journal filter) + stored for display.
    sb.add_text_field(FIELD_JOURNAL, stored=True, tokenizer_name="raw", index_option="basic")
    sb.add_text_field(FIELD_ISSN, stored=True, tokenizer_name="raw", index_option="basic")
    sb.add_text_field(FIELD_JOURNAL_ABBR, stored=True, tokenizer_name="raw", index_option="basic")

    # Authors: analyzed (default tokenizer -> positions) for phrase matching; raw copy stored.
    sb.add_text_field(
        FIELD_AUTHORS, stored=False, tokenizer_name="default", index_option="position"
    )
    sb.add_text_field(FIELD_AUTHORS_STORED, stored=True, tokenizer_name="raw", index_option="basic")
    sb.add_text_field(
        FIELD_EMAIL_AUTHOR, stored=False, tokenizer_name="default", index_option="position"
    )
    sb.add_text_field(FIELD_EMAIL, stored=True, tokenizer_name="raw", index_option="basic")

    # Publication type (analyzed for the `type` filter).
    sb.add_text_field(
        FIELD_PUBLICATION_TYPE, stored=False, tokenizer_name="default", index_option="position"
    )

    # Exact-match filter fields.
    sb.add_text_field(FIELD_LANGUAGE, stored=False, tokenizer_name="raw", index_option="basic")
    sb.add_integer_field(FIELD_YEAR, stored=True, indexed=True, fast=True)
    sb.add_integer_field(FIELD_PMC_MONTHS, stored=True, indexed=True, fast=True)

    sb.add_boolean_field(FIELD_RECENT, stored=False, indexed=True)
    sb.add_boolean_field(FIELD_OPEN_ACCESS, stored=True, indexed=True)
    sb.add_boolean_field(FIELD_MEDLINE_INDEXED, stored=True, indexed=True)
    sb.add_boolean_field(FIELD_NIHPA, stored=True, indexed=True)

    return sb.build()
