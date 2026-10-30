"""JANE shared search core.

Single source of truth for text normalization, index schema, MoreLikeThis term
extraction, scoring/aggregation, and data models. Used by BOTH jane_indexer (write
path) and jane_server (query path) so index-time and query-time tokenization can never
drift. See IMPLEMENTATION_NOTES.md #0 and MODERNIZATION_PLAN.md section 4.
"""
